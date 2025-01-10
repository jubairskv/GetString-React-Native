import React, {useState} from 'react';
import {View, Button, ActivityIndicator, Alert, StyleSheet} from 'react-native';
import {NativeModules} from 'react-native';

const {CameraModule} = NativeModules;

// // Function to upload the image
// const uploadImage = async byteArray => {
//   try {
//     console.log('Uploading image with byteArray:', byteArray); // Log the byteArray being sent
//     const response = await CameraModule.sendImageToApi(byteArray);
//     console.log('API Response:', response); // This will log the response received from the native module

//     // If the response is successful, show a success alert
//     Alert.alert('Success', 'Image uploaded successfully!');
//   } catch (error) {
//     // If there's an error, log it and show an alert
//     console.error('Error uploading image:', error);
//     Alert.alert('Error', 'Failed to upload image');
//   }
// };

const App = () => {
  const [isLoading, setIsLoading] = useState(false);

  // Launch the camera and capture an image
  const launchCamera = async () => {
    try {
      setIsLoading(true);
      const response = await CameraModule.startCameraPreview();
      console.log('Camera Launched: ', response);
    } catch (error) {
      console.error('Error launching camera:', error);
      Alert.alert('Error', error.message);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      <Button
        title="Launch Camera"
        onPress={launchCamera}
        color="#007BFF"
        disabled={isLoading}
      />

      {isLoading && (
        <ActivityIndicator style={styles.loader} size="large" color="#007BFF" />
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5F5F5',
    padding: 16,
  },
  loader: {
    marginTop: 20,
  },
});

export default App;
