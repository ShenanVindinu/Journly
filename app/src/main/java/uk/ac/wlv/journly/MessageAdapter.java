package uk.ac.wlv.journly;

import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    // The screen implements this to react to the adapter
    public interface Listener {
        void onMessageClick(Message message);          // normal tap
        void onSelectionChanged(int selectedCount);    // selection grew or shrank
    }

    private final List<Message> messages = new ArrayList<>();
    // Ids of the selected messages. Selection mode is on while this is not empty.
    private final Set<Long> selectedIds = new LinkedHashSet<>();
    private final Listener listener;
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("d MMM, h:mm a", Locale.getDefault());

    public MessageAdapter(Listener listener) {
        this.listener = listener;
    }

    // Replace the list contents and redraw. Any selection is cleared.
    public void setMessages(List<Message> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        selectedIds.clear();
        notifyDataSetChanged();
        listener.onSelectionChanged(0);
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    // The full Message objects that are selected
    public List<Message> getSelectedMessages() {
        List<Message> result = new ArrayList<>();
        for (Message m : messages) {
            if (selectedIds.contains(m.getId())) {
                result.add(m);
            }
        }
        return result;
    }

    public void clearSelection() {
        selectedIds.clear();
        notifyDataSetChanged();
        listener.onSelectionChanged(0);
    }

    // Add the message to the selection, or remove it if it is already there
    private void toggleSelection(Message m, int position) {
        if (!selectedIds.remove(m.getId())) {
            selectedIds.add(m.getId());
        }
        if (position != RecyclerView.NO_POSITION) {
            notifyItemChanged(position);
        }
        listener.onSelectionChanged(selectedIds.size());
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

        // Selected cards get a light blue background and a tick
        boolean selected = selectedIds.contains(m.getId());
        holder.card.setCardBackgroundColor(
                Color.parseColor(selected ? "#D9F3F7" : "#FFFFFF"));
        holder.imageCheck.setVisibility(selected ? View.VISIBLE : View.GONE);

        // While selecting, a tap toggles the card. Otherwise it opens the message.
        holder.itemView.setOnClickListener(v -> {
            if (!selectedIds.isEmpty()) {
                toggleSelection(m, holder.getBindingAdapterPosition());
            } else {
                listener.onMessageClick(m);
            }
        });

        // A long press starts selection mode
        holder.itemView.setOnLongClickListener(v -> {
            if (selectedIds.isEmpty()) {
                toggleSelection(m, holder.getBindingAdapterPosition());
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // Holds the views of one card so they are not looked up again and again
    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView textContent;
        final TextView textTime;
        final ImageView imageThumb;
        final ImageView imageCheck;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            textContent = itemView.findViewById(R.id.textContent);
            textTime = itemView.findViewById(R.id.textTime);
            imageThumb = itemView.findViewById(R.id.imageThumb);
            imageCheck = itemView.findViewById(R.id.imageCheck);
        }
    }
}