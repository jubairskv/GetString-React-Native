import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, Button } from 'react-native';
import { NativeModules } from 'react-native';

const { MyModule } = NativeModules;

const App = () => {
  const [dummyString, setDummyString] = useState('');

  const fetchDummyString = async () => {
    try {
      const result = await MyModule.getDummyString();
      setDummyString(result);
    } catch (error) {
      console.error('Error fetching dummy string:', error);
    }
  };

  useEffect(() => {
    fetchDummyString(); // Fetch the dummy string on component mount
  }, []);

  return (
    <View style={styles.container}>
      <Text style={styles.text}>{dummyString || 'Loading...'}</Text>
      <Button title="Refresh Dummy String" onPress={fetchDummyString} />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f5f5f5',
  },
  text: {
    fontSize: 18,
    color: '#333',
    marginBottom: 20,
  },
});

export default App;
