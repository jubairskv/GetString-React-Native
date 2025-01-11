import React from 'react';
import { createStackNavigator } from '@react-navigation/stack';
import ImagePreviewScreen from './ImagePreviewScreen'; // Import your screen component
import App from './App';
import EventListenerComponent from './EventListenerComponent'; // Import EventListenerComponent

const Stack = createStackNavigator();

const AppNavigator = () => (
  <>
    {/* Add EventListenerComponent to the component tree */}
    <EventListenerComponent />
    
    <Stack.Navigator>
      <Stack.Screen name="Home" component={App} />
      <Stack.Screen name="DisplayScreen" component={ImagePreviewScreen} />
    </Stack.Navigator>
  </>
);

export default AppNavigator;
