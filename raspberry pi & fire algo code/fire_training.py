import os
import numpy as np
from PIL import Image

def preprocess_images(image_dir):
    images = []
    labels = []

    # loops over both fire and non-fire directories
    for label, folder_name in enumerate(['0', '1']):  # 0 for non-fire, 1 for fire
        folder_path = os.path.join(image_dir, folder_name)
        
        # check if folder exists
        if not os.path.exists(folder_path):
            print(f"Folder {folder_path} not found!")
            continue

        for filename in os.listdir(folder_path):
            file_path = os.path.join(folder_path, filename)
            try:
                img = Image.open(file_path)
                
                
                img = img.resize((64, 64))  # resize to fixed size
                
                # convert the image to a numpy array and normalise
                img_array = np.array(img).flatten() / 255.0  # normalise pixel values
                
                # make sure image size is consistennt
                if img_array.shape == (64*64*3,):  
                    images.append(img_array)
                    labels.append(label)
                else:
                    print(f"skipping image due to unexpected shape: {file_path}")
                    
            except Exception as e:
                print(f"error processing {file_path}: {e}")

    # make sure final arrays are consistent
    if len(images) == 0 or len(labels) == 0:
        print("No valid images found. Exiting...")
        return np.array([]), np.array([])

    return np.array(images), np.array(labels)

def sigmoid(x):
    return 1 / (1 + np.exp(-x))

# gradient descent
def train_logistic_regression(X, y, learning_rate=0.1, epochs=1000):
    n_samples, n_features = X.shape
    weights = np.zeros(n_features)
    bias = 0

    for epoch in range(epochs):
        # linear model
        linear_model = np.dot(X, weights) + bias
        y_predicted = sigmoid(linear_model)

        #gradients
        dw = (1 / n_samples) * np.dot(X.T, (y_predicted - y))
        db = (1 / n_samples) * np.sum(y_predicted - y)

        # update weights and bias
        weights -= learning_rate * dw
        bias -= learning_rate * db

        if epoch % 100 == 0:
            # loss
            loss = -(1 / n_samples) * np.sum(
                y * np.log(y_predicted + 1e-15) + (1 - y) * np.log(1 - y_predicted + 1e-15)
            )
            print(f"Epoch {epoch}, Loss: {loss:.4f}")

    return weights, bias

# train and save weights and bias
def train_model(image_dir, save_path="fire_detection_model"):
    print("preprocessing images...")
    X, y = preprocess_images(image_dir)
    if X.size == 0 or y.size == 0:
        print("no valid images found. exiting...")
        return

    print(f"Loaded {len(X)} images.")

    print("Training model...")
    weights, bias = train_logistic_regression(X, y)

    print(f"Saving model to {save_path}...")
    np.save(save_path + "_weights.npy", weights)
    np.save(save_path + "_bias.npy", bias)

if __name__ == "__main__":
    dataset_path = "/home/pi/homeguard/Fire-Detection"  # path to dataset folder
    train_model(dataset_path)
