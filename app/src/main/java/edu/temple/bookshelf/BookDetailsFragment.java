package edu.temple.bookshelf;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.io.File;

public class BookDetailsFragment extends Fragment {

    Context parentActivity;

    private static final String BOOK_KEY = "bookKey";
    private Book book;

    TextView titleTextView, authorTextView;
    ImageView bookCoverImageView;
    Button downDeleteButton;
    File audioBook;


    public BookDetailsFragment() {}

    public static BookDetailsFragment newInstance(Book book) {
        BookDetailsFragment fragment = new BookDetailsFragment();
        Bundle args = new Bundle();
        args.putParcelable(BOOK_KEY, book);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof MediaControlInterface)
            parentActivity = context;
        else
            throw new RuntimeException("Please implement MediaControlInterface");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            book = getArguments().getParcelable(BOOK_KEY);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_book_details, container, false);
        titleTextView = v.findViewById(R.id.titleTextView);
        authorTextView = v.findViewById(R.id.authorTextView);
        bookCoverImageView = v.findViewById(R.id.coverImageView);
        try {
            audioBook = new File(parentActivity.getExternalFilesDir(null).toString(), book.getId() + ".mp3");
        } catch (Exception e){

        }
        //audioBook = new File(parentActivity.getExternalFilesDir(null).toString(), book.getId() + ".mp3");

        v.findViewById(R.id.playButton).setOnClickListener(view -> {
            ((MediaControlInterface)parentActivity).play(book.getId());
        });
        downDeleteButton = v.findViewById(R.id.downloadDeleteButton);

        try {
            if (audioBook.exists()) {
                downDeleteButton.setText("Delete");
            } else {
                downDeleteButton.setText("Download");
            }
        } catch (Exception e) {

        }

        downDeleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (audioBook.exists()) {
                    audioBook.delete();
                    downDeleteButton.setText("Download");
                } else {
                    downDeleteButton.setText("Delete");
                    ((MediaControlInterface)parentActivity).downDeleteButtonClicked(book);
                }


            }
        });

        if (book != null)
            changeBook(book);

        return v;
    }



    public void changeBook(Book book) {
        this.book = book;
        titleTextView.setText(book.getTitle());
        authorTextView.setText(book.getAuthor());
        Picasso.get().load(book.getCoverUrl()).into(bookCoverImageView);
    }

    interface MediaControlInterface {
        void play (int bookId);
        void downDeleteButtonClicked(Book book);
    }



}
