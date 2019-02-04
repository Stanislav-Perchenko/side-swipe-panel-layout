package com.alperez.samples.slider.utils;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.alperez.samples.slider.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Created by stanislav.perchenko on 1/30/2019
 */
public class Fragment2 extends Fragment {

    private View vContent;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (vContent == null) {
            vContent = inflater.inflate(R.layout.fragment_2, container, false);
        } else {
            container.removeView(vContent);
        }
        return vContent;
    }
}
