package com.alperez.samples.slider.utils;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListView;

import com.alperez.samples.slider.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Created by stanislav.perchenko on 1/30/2019
 */
public class Fragment1 extends Fragment {

    private final String dataItems[] = {"Line 1", "Line 2", "Line 3", "Line 4", "Line 5", "Line 6", "Line 7", "Line 8", "Line 9", "Line 10", "Line 11", "Line 12", "Line 13", "Line 14", "Line 15", "Line 16", "Line 17", "Line 18", "Line 19", "Line 20", "Line 21", "Line 22", "Line 23", "Line 24", "Line 25", "Line 26", "Line 27", "Line 28", "Line 29", "Line 30"};


    private View vContent;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (vContent == null) {
            vContent = new FrameLayout(inflater.getContext());
            vContent.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ListView vList = new ListView(inflater.getContext());
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.bottomMargin = 24;
            lp.topMargin = 24;
            lp.leftMargin = 16;
            lp.rightMargin = 16;
            vList.setLayoutParams(lp);
            ((FrameLayout) vContent).addView(vList);

            vList.setAdapter(new MyAdapter(inflater.getContext(), R.layout.list_item, dataItems));
        } else {
            container.removeView(vContent);
        }
        return vContent;
    }
}
