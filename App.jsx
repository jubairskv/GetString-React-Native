import React, {useState, useEffect} from 'react';
import {
  View,
  Button,
  Alert,
  Image,
  Text,
  StyleSheet,
  ActivityIndicator,
  TouchableOpacity,
  NativeEventEmitter,
  NativeModules,
} from 'react-native';

const {CameraModule} = NativeModules;
const cameraEventEmitter = new NativeEventEmitter(CameraModule);

const App = () => {
  const [imageData, setImageData] = useState(null);
  const [imagePath, setImagePath] = useState(null);
  const [imageFileName, setImageFileName] = useState(null); // New state for filename
  const [isLoading, setIsLoading] = useState(false);
  const [viewMode, setViewMode] = useState('file'); // 'file' or 'base64'

  useEffect(() => {
    const eventListener = cameraEventEmitter.addListener(
      'ImageCaptured',
      event => {
        setIsLoading(true);
        try {
          const {imageData, imagePath, imageFileName} = event; // Include imageFileName
          setImageData(imageData);
          setImagePath(imagePath);
          setImageFileName(imageFileName); // Set the image filename

          // Log the image data to console
          console.log('Captured Image Path:', imagePath);
          console.log('Captured Image Filename:', imageFileName); // Log the filename
          console.log(
            'Captured Image Data (Base64):',
            imageData ? 'Base64 data received' : 'No base64 data',
          );
        } catch (error) {
          console.error('Error processing image:', error);
          Alert.alert('Error', 'Failed to process image');
        } finally {
          setIsLoading(false);
        }
      },
    );

    return () => {
      eventListener.remove();
    };
  }, []);

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

  const toggleViewMode = () => {
    setViewMode(current => (current === 'file' ? 'base64' : 'file'));
  };

  const getImageSource = () => {
    if (viewMode === 'file' && imagePath) {
      return {uri: `file://${imagePath}`}; // Show image from file path
    }
    if (viewMode === 'base64' && imageData) {
      return {uri: `data:image/jpeg;base64,${imageData}`}; // Show image from Base64 string
    }
    return null;
  };

  const renderImage = () => {
    const source = getImageSource();
    if (!source) return null;

    return (
      <View style={styles.imageContainer}>
        <Text style={styles.text}>Captured Image ({viewMode} view):</Text>
        <Image
          source={source}
          style={styles.image}
          resizeMode="contain"
          onError={error => {
            console.error('Image loading error:', error);
            Alert.alert('Error', 'Failed to load image');
          }}
        />
        <TouchableOpacity style={styles.toggleButton} onPress={toggleViewMode}>
          <Text style={styles.toggleText}>
            Switch to {viewMode === 'file' ? 'Base64' : 'File'} View
          </Text>
        </TouchableOpacity>
        {/* Display the Base64 image data */}
        {imageData && viewMode === 'base64' && (
          <Text style={styles.imageDataText}>
            Base64 Image Data: {imageData}
          </Text>
        )}
        {/* Display the image filename */}
        {imageFileName && (
          <Text style={styles.filenameText}>Filename: {imageFileName}</Text>
        )}
      </View>
    );
  };

  return (
    <View style={styles.container}>
      <Button
        title="Launch Camera"
        onPress={launchCamera}
        color="#007BFF"
        disabled={isLoading}
      />

      {isLoading ? (
        <ActivityIndicator style={styles.loader} size="large" color="#007BFF" />
      ) : imageData || imagePath ? (
        renderImage()
      ) : (
        <Text style={styles.placeholder}>No image captured yet.</Text>
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
  imageContainer: {
    marginTop: 20,
    alignItems: 'center',
    width: '100%',
  },
  text: {
    fontSize: 18,
    marginBottom: 10,
    color: '#333',
  },
  image: {
    width: 300,
    height: 300,
    resizeMode: 'contain',
    backgroundColor: '#fff',
    borderRadius: 8,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 2},
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
    elevation: 5,
  },
  toggleButton: {
    marginTop: 16,
    backgroundColor: '#007BFF',
    padding: 12,
    borderRadius: 8,
    width: '80%',
  },
  toggleText: {
    color: '#fff',
    textAlign: 'center',
    fontSize: 16,
    fontWeight: '600',
  },
  loader: {
    marginTop: 20,
  },
  placeholder: {
    marginTop: 20,
    fontSize: 16,
    color: '#666',
  },
  imageDataText: {
    marginTop: 10,
    fontSize: 14,
    color: '#007BFF',
    textAlign: 'center',
    paddingHorizontal: 10,
  },
  filenameText: {
    marginTop: 10,
    fontSize: 14,
    color: '#333',
    textAlign: 'center',
    fontWeight: 'bold',
  },
});

export default App;
