package edu.temple.bookshelf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import edu.temple.audiobookplayer.AudiobookService;

public class MainActivity extends AppCompatActivity implements BookListFragment.BookSelectedInterface, BookDetailsFragment.MediaControlInterface {

    final String SEARCH_TERM_KEY = "search";
    final String NOWPLAYING_KEY = "npkey";
    final String NOWPLAYING_ID = "npid";
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
    int progress;
    int audiobookId;

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

    @Override
    public void downDeleteButtonClicked(Book book) {
        final int id = book.getId();
        Thread thread = new Thread() {
            @Override
            public void run() {
                downloadBookHandler.sendMessage(download(id));
            }
        };
        thread.start();
    }

    private Message download(int id) {
        URL downloadBookURL;
        String path = getExternalFilesDir(null).toString();
        File output = new File(path, id + ".mp3");
        output.getParentFile().mkdirs();

        try {
            downloadBookURL = new URL("https://kamorris.com/lab/audlib/download.php?id=" + id);
            URLConnection connection = downloadBookURL.openConnection();
            int connectionLength = connection.getContentLength();
            DataInputStream stream = new DataInputStream(downloadBookURL.openStream());

            byte[] buffer = new byte[connectionLength];
            stream.readFully(buffer);
            stream.close();

            if(!output.exists()) {
                output.createNewFile();
            }

            DataOutputStream outputStream = new DataOutputStream(new FileOutputStream(output));
            outputStream.write(buffer);
            outputStream.flush();
            outputStream.close();

        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        Message message = Message.obtain();
        message.obj = "downloaded";
        return message;
    }

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
                if (currentBook == null) {
                    currentBook = library.getBookWithId(audiobookId);
                }
                nowPlayingTextView.setText(String.format(getString(R.string.now_playing), currentBook.getTitle()));
                progress = ((AudiobookService.BookProgress) message.obj).getProgress();
                bookProgressSeekBar.setProgress((int) ((float) progress / currentBook.getDuration() * bookProgressSeekBar.getMax()));
            } else {
                bookProgressSeekBar.setProgress(0);
                nowPlayingTextView.setText(R.string.nothing_playing);
            }
            return true;
        }
    });

    Handler downloadBookHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            Log.d("test", "anything");
            if (message == null) {
                return false;
            } else {
                return true;
            }

        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences sharedP = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedP.edit();

        serviceIntent = new Intent (this, AudiobookService.class);
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);

        fm = getSupportFragmentManager();
        library = new Library();

        // Check for fragments in both containers
        current1 = fm.findFragmentById(R.id.container_1);
        current2 = fm.findFragmentById(R.id.container_2);

        onePane = findViewById(R.id.container_2) == null;

        if (current1 == null) {
            String search = sharedP.getString(SEARCH_TERM_KEY, null);
            fetchBooks(search);
        } else {
            updateDisplay();
        }

        nowPlayingTextView = findViewById(R.id.nowPlayingTextView);
        nowPlayingTextView.setText("Now Playing: " + sharedP.getString(NOWPLAYING_KEY, ""));
        findViewById(R.id.searchButton).setOnClickListener(v -> {
            fetchBooks(((EditText) findViewById(R.id.searchBox)).getText().toString());
            editor.putString(SEARCH_TERM_KEY, ((EditText) findViewById(R.id.searchBox)).getText().toString());
            editor.commit();
        });
        findViewById(R.id.pauseButton).setOnClickListener(v -> pause());
        findViewById(R.id.stopButton).setOnClickListener(v -> stop());
        bookProgressSeekBar = findViewById(R.id.bookProgressSeekBar);

        bookProgressSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean byUser) {
                if (byUser && connected && (currentBook != null)) {
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
            SharedPreferences sharedP = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedP.edit();
            if (currentBook != null) {
                editor.putInt(String.valueOf(currentBook.getId()), progress);
                editor.commit();
            }
            currentBook = library.getBookWithId(bookId);
            File audioBook = new File(getExternalFilesDir(null).toString(), bookId + ".mp3");
            Log.d("filepath", audioBook.getAbsolutePath());
            // Start service when playing to ensure the book
            // plays continuously, even when activity restarts
            editor.putString(NOWPLAYING_KEY, currentBook.getTitle());
            editor.putInt(NOWPLAYING_ID, currentBook.getId());
            editor.commit();
            startService(serviceIntent);
            if (audioBook.exists()) {
                if (sharedP.getBoolean(String.valueOf(currentBook.getId())+"a", true)) {
                    mediaControlBinder.play(audioBook, 0);
                    editor.putBoolean(String.valueOf(currentBook.getId())+"a", false);
                    editor.apply();
                    editor.commit();
                } else {
                    mediaControlBinder.play(audioBook, sharedP.getInt(String.valueOf(currentBook.getId()), 0));
                }
                audiobookId = bookId;
            } else {
                mediaControlBinder.play(bookId);
            }

        }
    }

    public void stop () {
        mediaControlBinder.stop();
        // Service will now stop once the activity unbinds
        stopService(serviceIntent);
        SharedPreferences sharedP = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedP.edit();
        editor.putInt(String.valueOf(currentBook.getId()), 0);
        editor.putBoolean(String.valueOf(currentBook.getId())+"a", true);
        editor.apply();
        editor.commit();
        Log.d("filepath", currentBook.getId() + " " + sharedP.getInt(String.valueOf(currentBook.getId()),0 ));

    }

    public void pause () {
        SharedPreferences sharedP = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedP.edit();
        editor.putInt(String.valueOf(currentBook.getId()), progress);
        editor.commit();
        mediaControlBinder.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnection);
    }
}
