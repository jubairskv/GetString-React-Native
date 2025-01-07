import React, {useState} from 'react';
import {View, Button, Alert, Text, StyleSheet} from 'react-native';
import {NativeModules} from 'react-native';

const {MyModule} = NativeModules;

const App = () => {
  const [isCameraLaunched, setIsCameraLaunched] = useState(false);

  const launchCamera = async () => {
    try {
      const response = await MyModule.startCameraPreview();
      setIsCameraLaunched(true);
      // Alert.alert('Success', response);
    } catch (error) {
      setIsCameraLaunched(false);
      Alert.alert('Error', error.message);
    }
  };

  return (
    <View style={styles.container}>
      <Button title="Launch Camera" onPress={launchCamera} color="#007BFF" />
      {isCameraLaunched && (
        <View style={styles.statusContainer}>
          <Text style={styles.statusText}>Camera Launched Successfully!</Text>
        </View>
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
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 20,
    color: '#333',
  },
  statusContainer: {
    marginTop: 20,
    padding: 10,
    backgroundColor: '#D4EDDA',
    borderRadius: 8,
    borderColor: '#C3E6CB',
    borderWidth: 1,
  },
  statusText: {
    color: '#155724',
    fontWeight: '600',
  },
});

export default App;
