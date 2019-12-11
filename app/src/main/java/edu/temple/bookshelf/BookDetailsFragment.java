package edu.temple.bookshelf;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

public class BookDetailsFragment extends Fragment {

    MediaControlInterface parentActivity;

    private static final String BOOK_KEY = "bookKey";
    private Book book;

    TextView titleTextView, authorTextView;
    ImageView bookCoverImageView;


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
            parentActivity = (MediaControlInterface) context;
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

        v.findViewById(R.id.playButton).setOnClickListener(view -> {
            parentActivity.play(book.getId());
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
    }

}
