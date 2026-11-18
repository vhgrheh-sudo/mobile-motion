package com.sarvesh173.aimobilemotions;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        startForegroundService(new Intent(this, AgentService.class));
        ((TextView) findViewById(R.id.status)).setText(
            "AI Mobile Motions\nagent control: TCP 4091\nself-test: {\"cmd\":\"selftest\"}");
    }
}
