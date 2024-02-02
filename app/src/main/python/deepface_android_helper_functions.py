#!/usr/bin/env python3

# Generate FaceNet embeddings, person folder names & person image counts as npy files,
# given a dataset folder with person folders containing images of each person

# Standard Library imports
import os

# Third Party imports
from deepface.commons import functions
from deepface.models.FacialRecognition import FacialRecognition
from deepface.basemodels.Facenet import InceptionResNetV2

from sklearn.svm import SVC
from sklearn.preprocessing import LabelEncoder
import joblib

import cv2
import numpy as np
import pickle

# Local Application/Library specific imports


def get_image_files(folder):
    image_extensions = ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.tiff', '.tif', '.webp', '.ico']  # Add more if needed
    image_files = [f for f in os.listdir(folder) if any(f.lower().endswith(ext) for ext in image_extensions)]
    return sorted(image_files)


def load_facenet512_model_from_h5_file(facenet512_h5_weights_path):
    model = InceptionResNetV2(dimension=512)
    model.load_weights(facenet512_h5_weights_path)

    return model


# class FaceNet512dClient(FacialRecognition):
#     """
#     FaceNet-1512d model class
#     """

#     def __init__(self, facenet512_h5_weights_path):
#         self.model = load_facenet512_model_from_h5_file(facenet512_h5_weights_path)
#         self.model_name = "FaceNet-512d"

#     def find_embeddings(self, img: np.ndarray):
#         """
#         find embeddings with FaceNet-512d model
#         Args:
#             img (np.ndarray): pre-loaded image in BGR
#         Returns
#             embeddings (list): multi-dimensional vector
#         """
#         # model.predict causes memory issue when it is called in a for loop
#         # embedding = model.predict(img, verbose=0)[0].tolist()
#         return self.model(img, training=False).numpy()[0].tolist()
    

# # def get_facenet_model(facenet512_h5_weights_path):
# #     facenet_model = FaceNet512dClient(facenet512_h5_weights_path)
# #     return facenet_model
    
# facenet_model = FaceNet512dClient("facenet512_weights.h5")


class FaceNet512dClient(FacialRecognition):
    """
    FaceNet-512d model class
    """

    def __init__(self, facenet512_h5_weights_path):
        # Attempt to load the model and set the boolean attribute
        self.model_loaded = self.load_facenet512_model(facenet512_h5_weights_path)
        self.model_name = "FaceNet-512d"

    def load_facenet512_model(self, facenet512_h5_weights_path):
        try:
            self.model = load_facenet512_model_from_h5_file(facenet512_h5_weights_path)
            return True  # Loading successful
        except Exception as e:
            print(f"Error loading FaceNet-512d model: {e}")
            return False  # Loading failed

    def find_embeddings(self, img: np.ndarray):
        """
        find embeddings with FaceNet-512d model
        Args:
            img (np.ndarray): pre-loaded image in BGR
        Returns:
            embeddings (list): multi-dimensional vector
        """
        if not self.model_loaded:
            print("FaceNet-512d model has not been loaded successfully.")
            return None

        return self.model(img, training=False).numpy()[0].tolist()

# Example usage:
# facenet_model = FaceNet512dClient("facenet512_weights.h5")
def get_facenet_model(facenet512_h5_weights_path="facenet512_weights.h5"):
    facenet_model = FaceNet512dClient(facenet512_h5_weights_path)
    return facenet_model
#
# if facenet_model.model_loaded:
#     print("FaceNet-512d model has been successfully loaded.")
# else:
#     print("Failed to load FaceNet-512d model.")


def get_deepface_facenet_embedding(image_path):
    # Pre-processing
    target_size = (160, 160)
    img, _ = functions.load_image(image_path)  # BGR image from image_path
    if len(img.shape) == 4:
            img = img[0]  # e.g. (1, 224, 224, 3) to (224, 224, 3)
    if len(img.shape) == 3:
        img = cv2.resize(img, target_size)
        img = np.expand_dims(img, axis=0)
        # when called from verify, this is already normalized. But needed when user given.
        if img.max() > 1:
            img = (img.astype(np.float32) / 255.0).astype(np.float32)

    # Custom normalization for Facenet512
    # img = functions.normalize_input(img=img, normalization="Facenet")
    mean, std = img.mean(), img.std()
    img = (img - mean) / std
    # img = functions.normalize_input(img=img, normalization="Facenet2018")
    # img /= 127.5
    # img -= 1

    embedding = facenet_model.find_embeddings(img)

    return embedding


def generate_person_embeddings_pickle_file(person_folder_path):
    person_folder_name = os.path.basename(person_folder_path)
    X = []  # To store embeddings

    image_files = get_image_files(person_folder_path)

    for image_file in image_files:
        image_file_path = os.path.join(person_folder_path, image_file)
        face_embedding = get_deepface_facenet_embedding(image_file_path)
        if face_embedding is not None:
            X.append(face_embedding)

    person_pickle_file_path = f"{person_folder_name}.pkl"
    with open(person_pickle_file_path, 'wb') as file:
        pickle.dump(X, file)


def load_person_embeddings_pickle_file(person_pickle_file_path):
    with open(person_pickle_file_path, 'rb') as file:
        X = pickle.load(file)
    return X


def train_svm_classifier_on_person_embeddings(person_pickle_file_paths):
    person_pickle_file_paths = list(person_pickle_file_paths)

    X_train = []
    y_train = []

    for person_pickle_path in person_pickle_file_paths:
        person_class_name = os.path.basename(person_pickle_path).rsplit('.', 1)[0]
        X = load_person_embeddings_pickle_file(person_pickle_path)
        y = [person_class_name] * len(X)

        X_train.extend(X)
        y_train.extend(y)

    label_encoder = LabelEncoder()
    y_train_encoded = label_encoder.fit_transform(y_train)

    C, gamma = 1, 0.1
    svm_classifier = SVC(kernel='rbf', probability=True, C=C, gamma=gamma)
    svm_classifier.fit(X_train, y_train_encoded)

    svm_pickle_file_path = "svm_classifier.pkl"
    encoder_pickle_file_path = "label_encoder.pkl"

    joblib.dump(svm_classifier, svm_pickle_file_path)
    joblib.dump(label_encoder, encoder_pickle_file_path)


if os.path.exists("svm_classifier.pkl"):
    svm_classifier = joblib.load("svm_classifier.pkl")
else:
    svm_classifier = None

if os.path.exists("label_encoder.pkl"):
    label_encoder = joblib.load("label_encoder.pkl")
else:
    label_encoder = None


def inference_svm(image_path):
    face_embedding = get_deepface_facenet_embedding(image_path)
    face_embedding_list = [face_embedding]

    probability_values = svm_classifier.predict_proba(face_embedding_list)
    y_pred = svm_classifier.predict(face_embedding_list)

    # Decode predicted labels back to original class names
    y_pred_original = label_encoder.inverse_transform(y_pred)

    pred_class, probability_list = y_pred_original[0], probability_values[0]
    max_confidence = max(probability_list)

    # Set probability threshold
    probability_threshold = 0.8


    return pred_class, max_confidence



if __name__ == '__main__':
    

    print("End of main()")
