package com.koreva.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(KorevaAlarmPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
