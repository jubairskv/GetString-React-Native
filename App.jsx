import React, {useState} from 'react';
import {View, Button, Alert, Text} from 'react-native';
import {NativeModules,NativeEventEmitter} from 'react-native';

const {CameraModule} = NativeModules;

const cameraEventEmitter = new NativeEventEmitter(CameraModule);

console.log(cameraEventEmitter)

const App = () => {
  const [isCameraLaunched, setIsCameraLaunched] = useState(false);

  const launchCamera = async () => {
    try {
      const response = await CameraModule.startCameraPreview();
      setIsCameraLaunched(true);
      Alert.alert('Success', response);
    } catch (error) {
      setIsCameraLaunched(false);
      Alert.alert('Error', error.message);
    }
  };

  return (
    <View style={{flex: 1, justifyContent: 'center', alignItems: 'center'}}>
      <Button title="Launch Camera" onPress={launchCamera} />
      {isCameraLaunched && <Text style={{marginTop: 20}}>Camera Launched</Text>}
    </View>
  );
};

export default App;
