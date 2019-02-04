package com.alperez.samples.slider.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.alperez.samples.slider.R;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Created by stanislav.perchenko on 1/31/2019
 */
public class LauncherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
        findViewById(R.id.btn1).setOnClickListener(this::onClick);
        findViewById(R.id.btn2).setOnClickListener(this::onClick);
        findViewById(R.id.btn3).setOnClickListener(this::onClick);
        findViewById(R.id.btn4).setOnClickListener(this::onClick);
    }


    private void onClick(View v) {
        Class<? extends Activity> actCls;
        Bundle extras = new Bundle();
        if (v instanceof TextView) {
            extras.putString(MyDrawerActivity.ARG_SCREEN_TITLE, ((TextView) v).getText().toString());
        }
        switch (v.getId()) {
            case R.id.btn1:
                actCls = MyDrawerActivity.class;
                extras.putInt(MyDrawerActivity.ARG_GRAVITY, Gravity.LEFT);
                break;
            case R.id.btn2:
                actCls = MyDrawerActivity.class;
                extras.putInt(MyDrawerActivity.ARG_GRAVITY, Gravity.RIGHT);
                break;
            case R.id.btn3:
                actCls = MyDrawerActivity.class;
                extras.putInt(MyDrawerActivity.ARG_GRAVITY, Gravity.LEFT);
                extras.putBoolean(MyDrawerActivity.ARG_FULL_SCREEN, true);
                break;
            case R.id.btn4:
                actCls = MyDrawerActivity.class;
                extras.putInt(MyDrawerActivity.ARG_GRAVITY, Gravity.RIGHT);
                extras.putBoolean(MyDrawerActivity.ARG_FULL_SCREEN, true);
                break;
            default:
                actCls = null;
        }
        if (actCls != null) startActivity(new Intent(this, actCls).putExtras(extras));
    }
}
