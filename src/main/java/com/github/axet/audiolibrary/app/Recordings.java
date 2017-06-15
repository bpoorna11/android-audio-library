package com.github.axet.audiolibrary.app;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.StatFs;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.androidlibrary.animations.RemoveItemAnimation;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.PopupShareActionProvider;
import com.github.axet.audiolibrary.R;
import com.github.axet.audiolibrary.animations.RecordingAnimation;
import com.github.axet.audiolibrary.encoders.Factory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class Recordings extends ArrayAdapter<File> implements AbsListView.OnScrollListener {
    public static String TAG = Recordings.class.getSimpleName();

    static final int TYPE_COLLAPSED = 0;
    static final int TYPE_EXPANDED = 1;
    static final int TYPE_DELETED = 2;

    public static class SortFiles implements Comparator<File> {
        @Override
        public int compare(File file, File file2) {
            if (file.isDirectory() && file2.isFile())
                return -1;
            else if (file.isFile() && file2.isDirectory())
                return 1;
            else
                return file.getPath().compareTo(file2.getPath());
        }
    }

    public static class FileStats {
        public int duration;
        public long size;
        public long last;
    }

    Handler handler;
    Storage storage;
    MediaPlayer player;
    Runnable updatePlayer;
    int selected = -1;
    ListView list;
    int scrollState;
    Thread thread;

    Map<File, FileStats> cache = new TreeMap<>();

    Map<File, Integer> durations = new TreeMap<>();

    public Recordings(Context context, ListView list) {
        super(context, 0);
        this.list = list;
        this.handler = new Handler();
        this.storage = new Storage(context);
        this.list.setOnScrollListener(this);
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        this.scrollState = scrollState;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
    }

    public void scan(File dir, final Runnable done) {
        final List<File> ff = storage.scan(dir);

        final Thread old = thread;

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (old != null) {
                    old.interrupt();
                    try {
                        old.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                final Thread t = Thread.currentThread();
                final Map<File, Integer> durations = new TreeMap<>();
                final ArrayList<File> all = new ArrayList<>();
                for (File f : ff) {
                    if (t.isInterrupted())
                        return;
                    if (f.isFile()) {
                        FileStats fs = cache.get(f);
                        if (fs != null) {
                            long last = f.lastModified();
                            long size = f.length();
                            if (last != fs.last || size != fs.size)
                                fs = null;
                        }
                        if (fs == null) {
                            fs = new FileStats();
                            fs.size = f.length();
                            fs.last = f.lastModified();
                            try {
                                MediaPlayer mp = MediaPlayer.create(getContext(), Uri.fromFile(f));
                                fs.duration = mp.getDuration();
                                cache.put(f, fs);
                                durations.put(f, fs.duration);
                                all.add(f);
                                mp.release();
                            } catch (Exception e) {
                                Log.d(TAG, f.toString(), e);
                            }
                        } else {
                            durations.put(f, fs.duration);
                            all.add(f);
                        }
                    }
                }
                for (File f : new TreeSet<>(cache.keySet())) {
                    if (!f.exists()) {
                        cache.remove(f);
                    }
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (t.isInterrupted())
                            return;
                        setNotifyOnChange(false);
                        clear();
                        Recordings.this.durations = durations;
                        for (File f : all) {
                            add(f);
                        }
                        sort();
                        notifyDataSetChanged();
                        if (done != null)
                            done.run();
                    }
                });
            }
        });
        thread.start();
    }

    public void sort() {
        sort(new SortFiles());
    }

    public void close() {
        playerStop();
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    public void load(Runnable done) {
        scan(storage.getStoragePath(), done);
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(getContext());

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.recording, parent, false);
            convertView.setTag(-1);
        }

        final View view = convertView;
        final View base = convertView.findViewById(R.id.recording_base);

        if ((int) convertView.getTag() == TYPE_DELETED) {
            RemoveItemAnimation.restore(base);
            convertView.setTag(-1);
        }

        final File f = getItem(position);

        TextView title = (TextView) convertView.findViewById(R.id.recording_title);
        title.setText(f.getName());

        TextView time = (TextView) convertView.findViewById(R.id.recording_time);
        time.setText(MainApplication.SIMPLE.format(new Date(f.lastModified())));

        TextView dur = (TextView) convertView.findViewById(R.id.recording_duration);
        dur.setText(MainApplication.formatDuration(getContext(), durations.get(f)));

        TextView size = (TextView) convertView.findViewById(R.id.recording_size);
        size.setText(MainApplication.formatSize(getContext(), f.length()));

        final View playerBase = convertView.findViewById(R.id.recording_player);
        playerBase.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });

        final Runnable delete = new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.delete_recording);
                builder.setMessage("...\\" + f.getName() + "\n\n" + getContext().getString(R.string.are_you_sure));
                builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        playerStop();
                        dialog.cancel();
                        RemoveItemAnimation.apply(list, base, new Runnable() {
                            @Override
                            public void run() {
                                f.delete();
                                view.setTag(TYPE_DELETED);
                                select(-1);
                                load(null);
                            }
                        });
                    }
                });
                builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                builder.show();
            }
        };

        final Runnable rename = new Runnable() {
            @Override
            public void run() {
                final OpenFileDialog.EditTextDialog e = new OpenFileDialog.EditTextDialog(getContext());
                e.setTitle(getContext().getString(R.string.rename_recording));
                e.setText(Storage.getNameNoExt(f));
                e.setPositiveButton(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        playerStop();
                        String ext = Storage.getExt(f);
                        String s = String.format("%s.%s", e.getText(), ext);
                        File ff = new File(f.getParent(), s);
                        f.renameTo(ff);
                        load(null);
                    }
                });
                e.show();

            }
        };

        if (selected == position) {
            RecordingAnimation.apply(list, convertView, true, scrollState == SCROLL_STATE_IDLE && (int) convertView.getTag() == TYPE_COLLAPSED);
            convertView.setTag(TYPE_EXPANDED);

            updatePlayerText(convertView, f);

            final View play = convertView.findViewById(R.id.recording_player_play);
            play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (player == null) {
                        playerPlay(playerBase, f);
                    } else if (player.isPlaying()) {
                        playerPause(playerBase, f);
                    } else {
                        playerPlay(playerBase, f);
                    }
                }
            });

            final View edit = convertView.findViewById(R.id.recording_player_edit);
            edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    rename.run();
                }
            });

            final View share = convertView.findViewById(R.id.recording_player_share);
            share.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Uri u = Uri.fromFile(f);

                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("audio/*");
                    intent.putExtra(Intent.EXTRA_EMAIL, "");
                    intent.putExtra(Intent.EXTRA_STREAM, u);
                    intent.putExtra(Intent.EXTRA_SUBJECT, f.getName());
                    intent.putExtra(Intent.EXTRA_TEXT, getContext().getString(R.string.shared_via, getContext().getString(R.string.app_name)));

                    if (Build.VERSION.SDK_INT < 11) {
                        getContext().startActivity(intent);
                    } else {
                        PopupShareActionProvider shareProvider = new PopupShareActionProvider(getContext(), share);
                        shareProvider.setShareIntent(intent);
                        shareProvider.show();
                    }
                }
            });

            View trash = convertView.findViewById(R.id.recording_player_trash);
            trash.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    delete.run();
                }
            });

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(-1);
                }
            });
        } else {
            RecordingAnimation.apply(list, convertView, false, scrollState == SCROLL_STATE_IDLE && (int) convertView.getTag() == TYPE_EXPANDED);
            convertView.setTag(TYPE_COLLAPSED);

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(position);
                }
            });
        }

        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                PopupMenu popup = new PopupMenu(getContext(), v);
                MenuInflater inflater = popup.getMenuInflater();
                inflater.inflate(R.menu.menu_context, popup.getMenu());
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == R.id.action_delete) {
                            delete.run();
                            return true;
                        }
                        if (item.getItemId() == R.id.action_rename) {
                            rename.run();
                            return true;
                        }
                        return false;
                    }
                });
                popup.show();
                return true;
            }
        });

        return convertView;
    }

    void playerPlay(View v, File f) {
        if (player == null)
            player = MediaPlayer.create(getContext(), Uri.fromFile(f));
        if (player == null) {
            Toast.makeText(getContext(), R.string.file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        player.start();

        updatePlayerRun(v, f);
    }

    void playerPause(View v, File f) {
        if (player != null) {
            player.pause();
        }
        if (updatePlayer != null) {
            handler.removeCallbacks(updatePlayer);
            updatePlayer = null;
        }
        updatePlayerText(v, f);
    }

    void playerStop() {
        if (updatePlayer != null) {
            handler.removeCallbacks(updatePlayer);
            updatePlayer = null;
        }
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
    }

    void updatePlayerRun(final View v, final File f) {
        boolean playing = updatePlayerText(v, f);

        if (updatePlayer != null) {
            handler.removeCallbacks(updatePlayer);
            updatePlayer = null;
        }

        if (!playing) {
            playerStop(); // clear player instance
            updatePlayerText(v, f); // update length
            return;
        }

        updatePlayer = new Runnable() {
            @Override
            public void run() {
                updatePlayerRun(v, f);
            }
        };
        handler.postDelayed(updatePlayer, 200);
    }

    boolean updatePlayerText(final View v, final File f) {
        ImageView i = (ImageView) v.findViewById(R.id.recording_player_play);

        final boolean playing = player != null && player.isPlaying();

        i.setImageResource(playing ? R.drawable.ic_pause_black_24dp : R.drawable.ic_play_arrow_black_24dp);

        TextView start = (TextView) v.findViewById(R.id.recording_player_start);
        SeekBar bar = (SeekBar) v.findViewById(R.id.recording_player_seek);
        TextView end = (TextView) v.findViewById(R.id.recording_player_end);

        int c = 0;
        int d = durations.get(f);

        if (player != null) {
            c = player.getCurrentPosition();
            d = player.getDuration();
        }

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser)
                    return;

                if (player == null)
                    playerPlay(v, f);

                if (player != null) {
                    player.seekTo(progress);
                    if (!player.isPlaying())
                        playerPlay(v, f);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        start.setText(MainApplication.formatDuration(getContext(), c));
        bar.setMax(d);
        bar.setKeyProgressIncrement(1);
        bar.setProgress(c);
        end.setText("-" + MainApplication.formatDuration(getContext(), d - c));

        return playing;
    }

    public void select(int pos) {
        selected = pos;
        notifyDataSetChanged();
        playerStop();
    }

    public int getSelected() {
        return selected;
    }
}