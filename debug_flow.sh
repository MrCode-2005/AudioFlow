#!/bin/bash
ADB_PATH="$HOME/Library/Android/sdk/platform-tools/adb"
DEVICE_ID="10BF7202FY00406"
PACKAGE_NAME="com.audioflow.player"

echo "Using ADB at: $ADB_PATH"
echo "Targeting device: $DEVICE_ID"
echo "Targeting package: $PACKAGE_NAME"

echo "Waiting for device..."
$ADB_PATH -s $DEVICE_ID wait-for-device

echo "Clearing old logs..."
$ADB_PATH -s $DEVICE_ID logcat -c

echo "----------------------------------------"
echo "LOGGING STARTED - PLEASE OPEN THE APP NOW"
echo "Press Ctrl+C to stop."
echo "----------------------------------------"

# Loop to handle app restarts
while true; do
    # Get PID (take only the first one if multiple)
    PID=$($ADB_PATH -s $DEVICE_ID shell pidof $PACKAGE_NAME | awk '{print $1}')
    
    if [ ! -z "$PID" ]; then
        echo "Found App PID: $PID"
        echo "Capturing logs... (Press Ctrl+C to stop)"
        # Capture logs for this PID
        $ADB_PATH -s $DEVICE_ID logcat --pid=$PID 
        
        # If logcat exits (app died), loop continues
        echo "App process ended or logcat stopped."
        sleep 1
    else
        echo "Waiting for app to start... (Press Start on phone)"
        sleep 2
    fi
done
