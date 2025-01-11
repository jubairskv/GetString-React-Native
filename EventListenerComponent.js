import React, { useEffect } from 'react';
import { NativeEventEmitter, NativeModules } from 'react-native';
import { useNavigation } from '@react-navigation/native';

const { CameraModule } = NativeModules;
const cameraEventEmitter = new NativeEventEmitter(CameraModule);

const EventListenerComponent = () => {
    const navigation = useNavigation();

    useEffect(() => {
        const subscription = cameraEventEmitter.addListener('navigateToScreen', (event) => {
            console.log("Event received:", event); // Log the event to check the parameters

            const { screen } = event;
            if (screen) {
                console.log("Navigating to screen:", screen); // Log the screen being navigated to
                navigation.navigate(screen); // Navigate to the specified screen
            }
        });

        return () => subscription.remove(); // Clean up listener
    }, [navigation]);

    return null; // Add this component to your app tree
};

export default EventListenerComponent;
