import React, {useState} from 'react';
import {View, Button, Alert, Text} from 'react-native';
import {NativeModules} from 'react-native';

const {MyModule} = NativeModules;

const App = () => {
  const [isCameraLaunched, setIsCameraLaunched] = useState(false);

  const launchCamera = async () => {
    try {
      const response = await MyModule.startCameraPreview();
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
      {isCameraLaunched && (
        <Text style={{marginTop: 20}}>Camera Launched</Text>
      )}
    </View>
  );
};

export default App;