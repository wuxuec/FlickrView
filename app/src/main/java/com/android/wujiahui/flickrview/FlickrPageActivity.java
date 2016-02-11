package com.android.wujiahui.flickrview;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.Fragment;

/**
 * Created by 武家辉 on 2016/2/5.
 */
public class FlickrPageActivity extends SingleFragmentActivity {

    public static Intent newIntent(Context context, Uri flickrPageUri) {
        Intent i = new Intent(context, FlickrPageActivity.class);
        i.setData(flickrPageUri);
        return i;
    }

    @Override
    protected Fragment createFragment() {
        return FlickrPageFragment.newInstance(getIntent().getData());
    }
}
