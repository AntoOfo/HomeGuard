import os
import time
import io
import queue
import numpy as np
from multiprocessing import Process, Queue, Event
from flask import Flask, Response, render_template
from PIL import Image, ImageDraw
from picamera2 import Picamera2
import Adafruit_DHT
import spidev
import firebase_admin
from firebase_admin import credentials, db
import RPi.GPIO as GPIO

# loads firebase credentials
FIREBASE_CREDENTIALS_PATH = os.getenv(
    'FIREBASE_CREDENTIALS_PATH',
    '/home/pi/homeguard/SECRETJSONFILE'
)

# sensors configuration
DHT_SENSOR = Adafruit_DHT.DHT11
TEMP_SENSOR_PIN = 14
WATER_LEVEL_CHANNEL = 1
GAS_LEVEL_CHANNEL = 0
SERVO_PIN = 18
BUZZER_PIN = 15


class PiMonitor:
    def __init__(self):
        # initialising camera
        self.picam2 = Picamera2()
        self.picam2.configure(self.picam2.create_preview_configuration(main={"size": (640, 480)}))
        self.picam2.start()

        # initialise firebase
        cred = credentials.Certificate(FIREBASE_CREDENTIALS_PATH)
        firebase_admin.initialize_app(cred, {
            'databaseURL': 'DATABASE LINK'
        })


        # firebase references
        self.temp_ref = db.reference('sensors/temperature')
        self.humidity_ref = db.reference('sensors/humidity')
        self.water_level_ref = db.reference('sensors/water_level')
        self.gas_ref = db.reference('sensors/gas')
        self.fire_ref = db.reference('sensors/fire_detection')
        self.trigger_ref = db.reference('trigger')
        self.buzzer_ref = db.reference('buzzer_trigger')

        if self.trigger_ref.get() is None:
            self.trigger_ref.set("reset")

        if self.buzzer_ref.get() is None:
            self.buzzer_ref.set("reset")

        

        # spi setup for mcp
        self.spi = spidev.SpiDev()
        self.spi.open(0, 0)
        self.spi.max_speed_hz = 1350000

        # queues and events setup
        self.sensor_queue = Queue()
        self.stop_event = Event()

        # setup for servo/buzzer
        GPIO.setmode(GPIO.BCM)
        GPIO.setup(SERVO_PIN, GPIO.OUT)
        GPIO.setup(BUZZER_PIN, GPIO.OUT)
        self.servo_pwm = GPIO.PWM(SERVO_PIN, 50)
        self.servo_pwm.start(0)

        self.buzzer = GPIO.PWM(BUZZER_PIN, 2000)
        self.buzzer.start(0)

        self.setup_firebase_listener()

        # loads trained model
        self.load_trained_model(
            '/home/pi/homeguard/fire_detection_model_weights.npy',
            '/home/pi/homeguard/fire_detection_model_bias.npy'
        )

        # flask setup
        self.app = Flask(__name__)
        self.setup_routes()

    def firebase_listener(self, event):
        if isinstance(event.data, dict) and event.data.get("status") == "triggered":
            print("Trigger detected. Moving servo...")
            self.move_servo_to_90_degrees()
            self.trigger_ref.set({"status": "reset", "timestamp": time.time() * 1000})

    def buzzer_listener(self, event):
        if isinstance(event.data, dict) and event.data.get("status") == "triggered":
            print("Buzzer triggered")
            self.activate_buzzer()
            self.buzzer_ref.set({"status": "reset", "timestamp": time.time() * 1000})

    def activate_buzzer(self):
        self.buzzer.start(50)

        for _ in range(3):  
            self.buzzer.ChangeDutyCycle(50)  
            time.sleep(0.5)  
            self.buzzer.ChangeDutyCycle(0)  # turn buzzer off
            time.sleep(0.5)

        self.buzzer.stop()

    def setup_firebase_listener(self):
            self.trigger_ref.listen(self.firebase_listener)
            self.buzzer_ref.listen(self.buzzer_listener)

    # reads data from mcp
    def read_adc_channel(self, channel):
        if 0 <= channel <= 7:
            adc = self.spi.xfer2([1, (8 + channel) << 4, 0])
            return ((adc[1] & 3) << 8) + adc[2]
        return -1

    # converts mcp data to percentages
    def convert_to_percentage(self, adc_value):
        max_adc_value = 4095
        return round((adc_value / max_adc_value) * 100, 2)

    def scale_mq135_reading(self, adc_value):
        MIN_ADC = 150   
        MAX_ADC = 2000  

        # ensure ADC is within expected range
        adc_value = max(MIN_ADC, min(adc_value, MAX_ADC))

        # normalises to percentage (0% - 100%)
        percentage = ((adc_value - MIN_ADC) / (MAX_ADC - MIN_ADC)) * 100

        # applys scaling factor to fine-tune sensitivity
        SCALING_FACTOR = 1.4
        percentage *= SCALING_FACTOR

        # Cap the value at 100% max
        return round(min(percentage, 100), 2)

    def read_dht_sensor(self):
        humidity, temperature = Adafruit_DHT.read(DHT_SENSOR, TEMP_SENSOR_PIN)
        return humidity, temperature

    # reads sensors and queues
    def sensor_reader(self):
        while not self.stop_event.is_set():
            humidity, temperature = self.read_dht_sensor()
            water_adc_value = self.read_adc_channel(WATER_LEVEL_CHANNEL)
            gas_adc_value = self.read_adc_channel(GAS_LEVEL_CHANNEL)
            scaled_gas_value = self.scale_mq135_reading(gas_adc_value)

            sensor_data = {
                'temperature': temperature,
                'humidity': humidity,
                'water_level': self.convert_to_percentage(water_adc_value),
                'gas_level': self.convert_to_percentage(scaled_gas_value)
            }

            try:
                self.sensor_queue.put(sensor_data, block=False)
            except queue.Full:
                print("Sensor queue is full, skipping data")

            time.sleep(0.5)

    # updates firebase
    def firebase_updater(self):
        while not self.stop_event.is_set():
            try:
                sensor_data = self.sensor_queue.get(timeout=1)
                current_time = time.time()

                if sensor_data['temperature'] is not None:
                    self.temp_ref.set({'value': sensor_data['temperature'], 'timestamp': current_time})
                if sensor_data['humidity'] is not None:
                    self.humidity_ref.set({'value': sensor_data['humidity'], 'timestamp': current_time})

                self.water_level_ref.set({'value': sensor_data['water_level'], 'timestamp': current_time})
                self.gas_ref.set({'value': sensor_data['gas_level'], 'timestamp': current_time})

                print(f"Updated Firebase: {sensor_data}")

            except queue.Empty:
                continue
            except Exception as e:
                print(f"Firebase update error: {e}")

    def move_servo_to_90_degrees(self):
        # moves servo to 90 degrees
        self.servo_pwm.ChangeDutyCycle(7.5)
        time.sleep(1)
        self.servo_pwm.ChangeDutyCycle(0)
                
    # resizes + normalises images
    def preprocess_image(self, image):
        image = image.resize((64, 64)) 
        image = np.array(image).astype(np.float32) / 255.0 
        return image.flatten()

    # sigmoid activation function
    def sigmoid(self, x):
        return 1 / (1 + np.exp(-x))
    
    # predicts if fire is in frame
    def predict_fire(self, frame):
        image = Image.fromarray(frame).convert("RGB")
        processed_image = self.preprocess_image(image)

        linear_model = np.dot(processed_image, self.weights) + self.bias
        prediction = self.sigmoid(linear_model)

        return prediction > 0.5
    
    def update_fire_detection_status(self, fire_detected):
        current_time = time.time()

        if fire_detected:
            fire_status = 'fire detected'
        else:
            fire_status = 'no fire'

        data = {
            'status': fire_status,
            'timestamp': current_time
        }

        try:
            self.fire_ref.set(data)
            print(f"Updated Firebase with fire status: {fire_status}")
        except Exception as e:
            print(f"Error updating Firebase: {e}")

    def draw_fire_box(self, frame):
        draw = ImageDraw.Draw(frame)
        box_coordinates = (50, 50, frame.width - 50, frame.height - 50) 
        draw.rectangle(box_coordinates, outline="green", width=4)

    def load_trained_model(self, weights_path, bias_path):
        self.weights = np.load(weights_path)
        self.bias = np.load(bias_path)

    def generate_camera_feed(self):
        fire_start_time = None
        fire_duration = 5
        last_fire_status = None
        last_print_time = time.time()

        while not self.stop_event.is_set():
            frame = self.picam2.capture_array()
            if frame is None:
                continue

            fire_detected = self.predict_fire(frame)
            fire_status ='no fire'

            if fire_detected:  
                if fire_start_time is None:
                    fire_start_time = time.time()  
                elif time.time() - fire_start_time >= fire_duration:
                    fire_status = 'fire detected'
            else:
                fire_start_time = None

            if fire_status != last_fire_status:
                self.update_fire_detection_status(fire_detected)
                if time.time() - last_print_time >= 5:
                    if fire_status == 'fire detected':
                        print(f"FIRE DETECTED. Status updated to {fire_status}")
                    else:
                        print("FIRE STATUS: No fire detected.")

                    last_fire_status = time.time()

            last_fire_status = fire_status

            img = Image.fromarray(frame).convert("RGB")
            if fire_detected:
                self.draw_fire_box(img)

            buffer = io.BytesIO()
            img.save(buffer, format="JPEG")
            buffer.seek(0)

            yield (b'--frame\r\n'
                b'Content-Type: image/jpeg\r\n\r\n' + buffer.getvalue() + b'\r\n')

            time.sleep(0.1)

    def setup_routes(self):
        @self.app.route('/')
        def index():
            return render_template('index.html')

        @self.app.route('/video_feed')
        def video_feed():
            return Response(self.generate_camera_feed(),
                            mimetype='multipart/x-mixed-replace; boundary=frame')
        

    def start_flask_app(self):
        self.app.run(host='0.0.0.0', port=5000, threaded=True)

    def run(self):
        try:

            sensor_process = Process(target=self.sensor_reader)
            sensor_process.start()

            firebase_process = Process(target=self.firebase_updater)
            firebase_process.start()

            self.start_flask_app()

        except KeyboardInterrupt:
            print("Stopping processes...")
            self.stop_event.set()
            sensor_process.join()
            firebase_process.join()
            self.spi.close()
            self.picam2.stop()
            print("Program stopped")

if __name__ == '__main__':
    monitor = PiMonitor()
    monitor.run()
