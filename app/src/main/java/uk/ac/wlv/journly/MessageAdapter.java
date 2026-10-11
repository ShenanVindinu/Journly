package uk.ac.wlv.journly;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    // The screen implements this to react to taps on a card
    public interface Listener {
        void onMessageClick(Message message);
        void onMessageLongClick(Message message);
    }

    private final List<Message> messages = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("d MMM, h:mm a", Locale.getDefault());

    public MessageAdapter(Listener listener) {
        this.listener = listener;
    }

    // Replace the list contents and redraw
    public void setMessages(List<Message> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message m = messages.get(position);

        holder.textContent.setText(m.getContent());
        holder.textTime.setText(timeFormat.format(new Date(m.getCreatedAt())));

        // Show the thumbnail only if the message has an image
        String path = m.getImagePath();
        if (path != null && !path.isEmpty()) {
            holder.imageThumb.setVisibility(View.VISIBLE);
            holder.imageThumb.setImageURI(Uri.fromFile(new File(path)));
        } else {
            holder.imageThumb.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onMessageClick(m));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onMessageLongClick(m);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // Holds the views of one card so they are not looked up again and again
    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textContent;
        final TextView textTime;
        final ImageView imageThumb;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textContent = itemView.findViewById(R.id.textContent);
            textTime = itemView.findViewById(R.id.textTime);
            imageThumb = itemView.findViewById(R.id.imageThumb);
        }
    }
}