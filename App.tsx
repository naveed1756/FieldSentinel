import React, { useEffect, useState } from 'react';
import { SafeAreaView, StatusBar, Text, View, Button } from 'react-native';
import { AuthScreen } from './src/screens/AuthScreen';
import { AttendanceLogScreen } from './src/screens/AttendanceLogScreen';
import { EnrollmentScreen } from './src/screens/EnrollmentScreen';
import DatabaseService from './src/services/DatabaseService';

const App = () => {
  const [dbState, setDbState] = useState('loading');
  const [currentScreen, setCurrentScreen] = useState('auth');

  useEffect(() => {
    const setupDB = async () => {
      try {
        await DatabaseService.initDB();
        setDbState('ready');
      } catch (error) {
        setDbState('error');
      }
    };
    setupDB();
  }, []);

  if (dbState === 'loading') {
    return (
      <View style={{ flex:1, justifyContent:'center', alignItems:'center' }}>
        <Text>Booting DB...</Text>
      </View>
    );
  }

  if (dbState === 'error') {
    return (
      <View style={{ flex:1, justifyContent:'center', alignItems:'center' }}>
        <Text>DB Error</Text>
      </View>
    );
  }

  return (
    <SafeAreaView style={{ flex: 1 }}>
      <StatusBar barStyle="dark-content" />

      {/* Navigation buttons */}
      <View style={{ flexDirection:'row', justifyContent:'space-around', padding:8, backgroundColor:'#eee' }}>
        <Button title="Enroll"     onPress={() => setCurrentScreen('enroll')} />
        <Button title="Verify"     onPress={() => setCurrentScreen('auth')}   />
        <Button title="Logs"       onPress={() => setCurrentScreen('logs')}   />
      </View>

      {/* Screens */}
      {currentScreen === 'auth'   && <AuthScreen />}
      {currentScreen === 'enroll' && <EnrollmentScreen />}
      {currentScreen === 'logs'   && <AttendanceLogScreen onBack={() => setCurrentScreen('auth')} />}

    </SafeAreaView>
  );
};

export default App;