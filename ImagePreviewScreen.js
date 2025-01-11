// ImagePreviewScreen.js
import React from 'react';
import { View, Text, Image, StyleSheet } from 'react-native';
//import { useSelector } from 'react-redux'; // Assuming you're using Redux to manage your state
//import { useSharedViewModel } from './path/to/your/sharedViewModel'; // Adjust import based on your structure

const ImagePreviewScreen = () => {
  //const sharedViewModel = useSharedViewModel(); // Access the shared view model that contains the image
  //const frontImage = sharedViewModel.frontImage; // Assuming `frontImage` is the processed image

  return (
    <View style={styles.container}>
      <Text style={styles.heading}>Image Preview</Text>
      
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  heading: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 20,
  },
  image: {
    width: '100%',
    height: 300,
    resizeMode: 'contain',
  },
});

export default ImagePreviewScreen;
