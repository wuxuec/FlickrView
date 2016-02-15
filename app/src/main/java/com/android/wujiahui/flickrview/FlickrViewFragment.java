package com.android.wujiahui.flickrview;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by 武家辉 on 2015/12/15.
 */
public class FlickrViewFragment extends VisiableFragment {

    private static final String TAG = "FlickrViewFragment";

    private RecyclerView mPhotoRecyclerView;
    private List<FlickrItem> mItems = new ArrayList<>();
    public static int lastPage = 0;
    private FlickrFetchr mFlickrFetchr;
    private boolean backgroundIsLoading = false;
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;
    private ProgressDialog mProgressDialog;

    public static FlickrViewFragment newInstance() {
        return new FlickrViewFragment();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);

//        setupAdapter();

        mFlickrFetchr = new FlickrFetchr();

//        new FetchItemTask().execute(lastPage);
        updateItems(lastPage++);

//        Intent i = PollService.newIntent(getActivity());
//        getActivity().startService(i);

//        PollService.setServiceAlarm(getActivity(), true);


        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
        mThumbnailDownloader.setThumbnailDownloadListener(
                new ThumbnailDownloader.ThumbnailDownloadListener<PhotoHolder>() {
                    @Override
                    public void onThumbnailDownloaded(PhotoHolder target, Bitmap thumbnail) {
                        Drawable drawable = new BitmapDrawable(getResources(), thumbnail);
                        target.bindDrawable(drawable);
                    }
                }
        );
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "Backgroud thread started");
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_flickr_view, container, false);

        mPhotoRecyclerView = (RecyclerView) v
                .findViewById(R.id.fragment_flickr_view_recycler_view);
        mPhotoRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 3));
        mPhotoRecyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {

            boolean isScrollToButtom = false;

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                GridLayoutManager gridLayoutManager = (GridLayoutManager) recyclerView.getLayoutManager();

                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int lastItemPosition = gridLayoutManager.findLastCompletelyVisibleItemPosition();

                    int totalItemsNumber = gridLayoutManager.getItemCount();

                    if (lastItemPosition == (totalItemsNumber - 1) && isScrollToButtom
                            && !backgroundIsLoading) {
                        Log.i(TAG, "it's loading a new page, the total items is "
                                + String.valueOf(totalItemsNumber));
                        Toast.makeText(getActivity(), "正在加载新一页", Toast.LENGTH_SHORT).show();
                        updateItems(lastPage++);
                    }

                    int firstVisiableItem = gridLayoutManager.findFirstVisibleItemPosition();
                    int lastVisiableItem = gridLayoutManager.findLastVisibleItemPosition();

                    preloadAdjacentImages(firstVisiableItem, lastVisiableItem);

                }

            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0) {
                    isScrollToButtom = true;
                } else {
                    isScrollToButtom = false;
                }
            }
        });

        mPhotoRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        Point size = new Point();
                        getActivity().getWindowManager().getDefaultDisplay().getSize(size);
                        int newColumns = (int) ((size.x * 3) / 1440);
                        if (newColumns != 3) {
                            GridLayoutManager gridLayoutManager = (GridLayoutManager)
                                    mPhotoRecyclerView.getLayoutManager();
                            gridLayoutManager.setSpanCount(newColumns);
                        }

                    }
                });

        setupAdapter();

        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mThumbnailDownloader.clearQueue();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_flickr_view, menu);

        final MenuItem searchItem = menu.findItem(R.id.menu_item_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();


        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d(TAG, "QueryTextSubmit: " + query);
                QueryPreferences.setStoredQuery(getActivity(), query);
                searchItem.collapseActionView();
//                updateItems(0);

                recoverPage();

                updateItems(lastPage++);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.d(TAG, "QueryTextChange: " + newText);
                return false;
            }
        });

        searchView.setOnSearchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String query = QueryPreferences.getStoredQuery(getActivity());
                searchView.setQuery(query, false);
            }
        });

        MenuItem toggleItem = menu.findItem(R.id.menu_item_toggle_polling);
        if (PollService.isServiceAlarmOn(getActivity())) {
            toggleItem.setTitle(R.string.stop_polling);
        } else {
            toggleItem.setTitle(R.string.start_polling);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_clear:
                QueryPreferences.setStoredQuery(getActivity(), null);
                recoverPage();
                updateItems(lastPage++);
                return true;

            case R.id.menu_item_toggle_polling:
                boolean shouldStartAlarm = !PollService.isServiceAlarmOn(getActivity());
                PollService.setServiceAlarm(getActivity(), shouldStartAlarm);
                getActivity().invalidateOptionsMenu();//update menu;
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateItems(int pageNumber) {
        String query = QueryPreferences.getStoredQuery(getActivity());
        new FetchItemTask(query).execute(pageNumber);
    }

    private void setupAdapter() {
        if (isAdded()) {
            mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder
        implements View.OnClickListener{

        private ImageView mItemImageView;
        private FlickrItem mFlickrItem;

        public PhotoHolder(View itemView) {
            super(itemView);

            mItemImageView = (ImageView) itemView
            .findViewById(R.id.fragment_flickr_view_image_view);
            itemView.setOnClickListener(this);
        }

        public void bindDrawable(Drawable drawable) {
            mItemImageView.setImageDrawable(drawable);
        }

        public void bindFlickrItem(FlickrItem flickrItem) {
            mFlickrItem = flickrItem;
        }

        @Override
        public void onClick(View view) {
//            Intent  i = new Intent(Intent.ACTION_VIEW,mFlickrItem.getPhotoPageUri());
            Intent i = FlickrPageActivity.newIntent(getActivity(), mFlickrItem.getFlickrPageUri());
            startActivity(i);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {

        private List<FlickrItem> mFlickrItems;
        public PhotoAdapter(List<FlickrItem> galleryItems) {
            mFlickrItems = galleryItems;
        }


        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.flickr_item, parent, false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoHolder holder, int position) {
            FlickrItem flickrItem = mFlickrItems.get(position);
            holder.bindFlickrItem(flickrItem);
            Drawable placeHolder = getResources().getDrawable(R.drawable.bill_up_close);
            holder.bindDrawable(placeHolder);
            mThumbnailDownloader.queueThumnail(holder, flickrItem.getUrl());

        }

        @Override
        public int getItemCount() {
            return mFlickrItems.size();
        }
    }

    private void preloadAdjacentImages(int firstPosition, int lastPosition) {
        int startPostion = Math.max(firstPosition - 10, 0);
        int endPosition = Math.min(lastPosition+10, mPhotoRecyclerView.getAdapter().getItemCount()-1);

        Log.i(TAG, "The last position is "+String.valueOf(endPosition));

        for (int i = startPostion; i <= endPosition; i++) {
            mThumbnailDownloader
                    .preloadBitmapIntoCache(mItems.get(i).getUrl());
//            Log.i(TAG, "The preload url is :  " + mItems.get(i).getUrl());

        }
    }

    private class FetchItemTask extends AsyncTask<Integer, Void, List<FlickrItem>> {

        private String mQuery;

        public FetchItemTask(String query) {
            mQuery = query;
        }

        @Override
        protected void onPreExecute() {
//            mProgressDialog = new ProgressDialog(getActivity());
//            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
//            mProgressDialog.setIndeterminate(true);
//            mProgressDialog.setTitle("FlickrView");
//            mProgressDialog.setMessage("It's loading....");
//            mProgressDialog.show();
        }

        @Override
        protected List<FlickrItem> doInBackground(Integer... voids) {

            backgroundIsLoading = true;
            //Log.i(TAG, "Loading page "+voids[0].toString());
//            String query = "robot"; //just for testing

            if (mQuery == null) {
                return new FlickrFetchr().fetchRecentPhotos(voids[0]);
            } else {
                return new FlickrFetchr().searchPhotos(mQuery, voids[0]);
            }

        }

        @Override
        protected void onPostExecute(List<FlickrItem> flickrItems) {
            backgroundIsLoading = false;

            if (mItems == null) {
                mItems = flickrItems;
                setupAdapter();
            } else {
                mItems.addAll(flickrItems);
                mPhotoRecyclerView.getAdapter().notifyDataSetChanged();
            }
//            if (mProgressDialog != null) {
//                mProgressDialog.dismiss();
//            }


        }
    }

    private void recoverPage() {
        mThumbnailDownloader.clearQueue();
        mThumbnailDownloader.clearCache();
        lastPage = 0;
        mItems.clear();

    }
}
