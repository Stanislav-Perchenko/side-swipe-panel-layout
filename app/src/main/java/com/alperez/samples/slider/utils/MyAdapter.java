package com.alperez.samples.slider.utils;

import android.content.Context;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

/**
 * Created by stanislav.perchenko on 1/31/2019
 */
public class MyAdapter extends ArrayAdapter<String> {
    public MyAdapter(@NonNull Context context, int resource, String... dataItems) {
        super(context, resource, dataItems);
    }
}
