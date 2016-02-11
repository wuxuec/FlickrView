package com.android.wujiahui.flickrview;

import android.content.Context;
import android.content.Intent;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class FlickrViewActivity extends SingleFragmentActivity {

    public static Intent newIntent(Context context) {
        return new Intent(context, FlickrViewActivity.class);
    }

    @Override
    protected Fragment createFragment() {
        return FlickrViewFragment.newInstance();
    }


}
