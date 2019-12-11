package edu.temple.bookshelf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

import edu.temple.audiobookplayer.AudiobookService;

public class MainActivity extends AppCompatActivity implements BookListFragment.BookSelectedInterface, BookDetailsFragment.MediaControlInterface {

    FragmentManager fm;
    BookDetailsFragment bookDetailsFragment;
    Book currentBook;
    TextView nowPlayingTextView;
    boolean onePane;
    Library library;
    Fragment current1, current2;
    Intent serviceIntent;
    AudiobookService.MediaControlBinder mediaControlBinder;
    boolean connected;
    SeekBar bookProgressSeekBar;

    private final String SEARCH_URL = "https://kamorris.com/lab/audlib/booksearch.php?search=";

    ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mediaControlBinder = (AudiobookService.MediaControlBinder) iBinder;
            mediaControlBinder.setProgressHandler(progressHandler);
            connected = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            connected = false;
        }
    };

    // Handler to receive downloaded books
    Handler bookHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            try {
                library.clear();
                JSONArray booksArray = new JSONArray((String) message.obj);
                for (int i = 0; i < booksArray.length(); i++) {
                    library.addBook(new Book(booksArray.getJSONObject(i)));
                }

                if(fm.findFragmentById(R.id.container_1) == null)
                    setUpDisplay();
                else
                    updateBooks();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return true;
        }
    });

    //Handler to receive book progress updates
    Handler progressHandler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message message) {

            if (message.obj != null) {
                currentBook = library.getBookWithId(((AudiobookService.BookProgress) message.obj).getBookId());
                nowPlayingTextView.setText(String.format(getString(R.string.now_playing), currentBook.getTitle()));
                int progress = ((AudiobookService.BookProgress) message.obj).getProgress();
                bookProgressSeekBar.setProgress((int) ((float) progress / currentBook.getDuration() * bookProgressSeekBar.getMax()));
            } else {
                bookProgressSeekBar.setProgress(0);
                nowPlayingTextView.setText(R.string.nothing_playing);
            }
            return true;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serviceIntent = new Intent (this, AudiobookService.class);
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);

        fm = getSupportFragmentManager();
        library = new Library();

        // Check for fragments in both containers
        current1 = fm.findFragmentById(R.id.container_1);
        current2 = fm.findFragmentById(R.id.container_2);

        onePane = findViewById(R.id.container_2) == null;

        if (current1 == null) {
            fetchBooks(null);
        } else {
            updateDisplay();
        }

        nowPlayingTextView = findViewById(R.id.nowPlayingTextView);
        findViewById(R.id.searchButton).setOnClickListener(v -> fetchBooks(((EditText) findViewById(R.id.searchBox)).getText().toString()));
        findViewById(R.id.pauseButton).setOnClickListener(v -> pause());
        findViewById(R.id.stopButton).setOnClickListener(v -> stop());
        bookProgressSeekBar = findViewById(R.id.bookProgressSeekBar);

        bookProgressSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean byUser) {
                if (byUser && connected) {
                    mediaControlBinder.seekTo((int) (((float) i / seekBar.getMax()) * currentBook.getDuration()));
                }
            }

            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setUpDisplay() {
        // If there are no fragments at all (first time starting activity)

        if (onePane) {
            current1 = ViewPagerFragment.newInstance(library);
            fm.beginTransaction()
                    .add(R.id.container_1, current1)
                    .commit();
        } else {
            current1 = BookListFragment.newInstance(library);
            bookDetailsFragment = new BookDetailsFragment();
            fm.beginTransaction()
                    .add(R.id.container_1, current1)
                    .add(R.id.container_2, bookDetailsFragment)
                    .commit();
        }

    }

    private void updateDisplay () {
        Fragment tmpFragment = current1;
        library = ((Displayable) current1).getBooks();
        if (onePane) {
            if (current1 instanceof BookListFragment) {
                current1 = ViewPagerFragment.newInstance(library);
                // If we have the wrong fragment for this configuration, remove it and add the correct one
                fm.beginTransaction()
                        .remove(tmpFragment)
                        .add(R.id.container_1, current1)
                        .commit();
            }
        } else {
            if (current1 instanceof ViewPagerFragment) {
                current1 = BookListFragment.newInstance(library);
                fm.beginTransaction()
                        .remove(tmpFragment)
                        .add(R.id.container_1, current1)
                        .commit();
            }
            if (current2 instanceof BookDetailsFragment)
                bookDetailsFragment = (BookDetailsFragment) current2;
            else {
                bookDetailsFragment = new BookDetailsFragment();
                fm
                        .beginTransaction()
                        .add(R.id.container_2, bookDetailsFragment)
                        .commit();
            }
        }

        bookDetailsFragment = (BookDetailsFragment) current2;
    }

    private void updateBooks() {
        ((Displayable) current1).setBooks(library);
    }

    @Override
    public void bookSelected(Book book) {
        if (bookDetailsFragment != null)
            bookDetailsFragment.changeBook(book);
    }

    private boolean isNetworkActive() {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private void fetchBooks(final String searchString) {
        new Thread() {
            @Override
            public void run() {
                if (isNetworkActive()) {

                    URL url;

                    try {
                        url = new URL(SEARCH_URL + (searchString != null ? searchString : ""));
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(
                                        url.openStream()));

                        StringBuilder response = new StringBuilder();
                        String tmpResponse;

                        while ((tmpResponse = reader.readLine()) != null) {
                            response.append(tmpResponse);
                        }

                        Message msg = Message.obtain();

                        msg.obj = response.toString();

                        Log.d("Books RECEIVED", response.toString());

                        bookHandler.sendMessage(msg);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } else {
                    Log.e("Network Error", "Cannot download books");
                }
            }
        }.start();
    }

    @Override
    public void play(int bookId) {
        if (connected) {
            currentBook = library.getBookWithId(bookId);
            // Start service when playing to ensure the book
            // plays continuously, even when activity restarts
            startService(serviceIntent);
            mediaControlBinder.play(bookId);
        }
    }

    public void stop () {
        mediaControlBinder.stop();
        // Service will now stop once the activity unbinds
        stopService(serviceIntent);
    }

    public void pause () {
        mediaControlBinder.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnection);
    }
}
