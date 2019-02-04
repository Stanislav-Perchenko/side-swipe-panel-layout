package com.alperez.samples.slider.activity;

import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;

import com.alperez.samples.slider.R;
import com.alperez.widget.customlayout.SideSwipePanelLayout;
import com.alperez.samples.slider.utils.MyAdapter;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * Created by stanislav.perchenko on 1/31/2019
 */
public class MyDrawerActivity extends AppCompatActivity {
    public static final String ARG_GRAVITY = "gravity";
    public static final String ARG_FULL_SCREEN = "full_screen";
    public static final String ARG_SCREEN_TITLE = "scr_title";


    private final String dataItems[] = {"Line 1", "Line 2", "Line 3", "Line 4", "Line 5", "Line 6", "Line 7", "Line 8", "Line 9", "Line 10", "Line 11", "Line 12", "Line 13", "Line 14", "Line 15", "Line 16", "Line 17", "Line 18", "Line 19", "Line 20", "Line 21", "Line 22", "Line 23", "Line 24", "Line 25", "Line 26", "Line 27", "Line 28", "Line 29", "Line 30"};

    private SideSwipePanelLayout vSlideContainer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResId());
        setupToolbar();


        vSlideContainer = (SideSwipePanelLayout) findViewById(R.id.side_container_layout);
        vSlideContainer.setDrawerLockMode(SideSwipePanelLayout.LOCK_MODE_UNLOCKED);
        if (getIntent().getBooleanExtra(ARG_FULL_SCREEN, false)) vSlideContainer.setMinDrawerMargin(0);

        ((ListView) findViewById(R.id.list)).setAdapter(new MyAdapter(this, R.layout.list_item, dataItems));
    }

    private int getLayoutResId() {
        switch (getIntent().getIntExtra(ARG_GRAVITY, Gravity.LEFT)) {
            case Gravity.LEFT:
                return R.layout.activity_my_drawer_left;
            case Gravity.RIGHT:
                return R.layout.activity_my_drawer_right;
            default:
                throw new IllegalStateException("Wrong Gravity parameter. Must be LEFT or RIGHT");
        }
    }

    private void setupToolbar() {
        ActionBar ab = getSupportActionBar();
        if (ab == null) {
            setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
            ab = getSupportActionBar();
        }
        if(ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle(getActivityTitle());
        }
    }

    private String getActivityTitle() {
        String title = getIntent().getStringExtra(ARG_SCREEN_TITLE);
        return (title == null) ? "" : title;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.my_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.menu_action_panel_more:
                vSlideContainer.openDrawer(true);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        if (vSlideContainer.isDrawerOpen()) {
            vSlideContainer.closeDrawer(true);
        } else {
            super.onBackPressed();
        }
    }
}
