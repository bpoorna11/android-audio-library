package com.github.axet.audiolibrary.app;

import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.audiolibrary.R;
import com.github.axet.audiolibrary.animations.RecordingAnimation;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class Recordings extends ArrayAdapter<Uri> implements AbsListView.OnScrollListener {
    public static String TAG = Recordings.class.getSimpleName();

    static final int TYPE_COLLAPSED = 0;
    static final int TYPE_EXPANDED = 1;
    static final int TYPE_DELETED = 2;

    Handler handler;
    Storage storage;
    MediaPlayer player;
    Runnable updatePlayer;
    int selected = -1;
    ListView list;
    int scrollState;
    Thread thread;

    ViewGroup toolbar;
    View toolbar_a;
    View toolbar_s;
    View toolbar_n;
    View toolbar_d;
    boolean toolbarFilterAll = true; // all or stars
    boolean toolbarSortName = true; // name or date

    Map<Uri, FileStats> cache = new TreeMap<>();

    Map<Uri, Integer> durations = new TreeMap<>();

    public static FileStats getFileStats(Map<String, ?> prefs, Uri f) {
        String json = (String) prefs.get(MainApplication.getFilePref(f) + MainApplication.PREFERENCE_DETAILS_FS);
        if (json != null && !json.isEmpty()) {
            return new FileStats(json);
        }
        return null;
    }

    public static void setFileStats(Context context, Uri f, Recordings.FileStats fs) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String p = MainApplication.getFilePref(f) + MainApplication.PREFERENCE_DETAILS_FS;
        SharedPreferences.Editor editor = shared.edit();
        editor.putString(p, fs.save().toString());
        editor.commit();
    }

    public class SortName implements Comparator<Uri> {
        @Override
        public int compare(Uri file, Uri file2) {
            return file.getPath().compareTo(file2.getPath());
        }
    }

    public class SortDate implements Comparator<Uri> {
        @Override
        public int compare(Uri file, Uri file2) {
            long l1 = cache.get(file).last;
            long l2 = cache.get(file2).last;
            return Long.valueOf(l1).compareTo(l2);
        }
    }

    public static class FileStats {
        public int duration;
        public long size;
        public long last;

        public FileStats() {
        }

        public FileStats(String json) {
            try {
                JSONObject j = new JSONObject(json);
                duration = j.getInt("duration");
                size = j.getLong("size");
                last = j.getLong("last");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        public JSONObject save() {
            try {
                JSONObject o = new JSONObject();
                o.put("duration", duration);
                o.put("size", size);
                o.put("last", last);
                return o;
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Recordings(Context context, ListView list) {
        super(context, 0);
        this.list = list;
        this.handler = new Handler();
        this.storage = new Storage(context);
        this.list.setOnScrollListener(this);
        load();
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        this.scrollState = scrollState;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
    }

    public void scan(final List<Uri> ff, final Runnable done) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        final Map<String, ?> prefs = shared.getAll();

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
                final Map<Uri, Integer> durations = new TreeMap<>();
                final ArrayList<Uri> all = new ArrayList<>();
                for (Uri f : ff) {
                    if (t.isInterrupted())
                        return;
                    FileStats fs = cache.get(f);
                    if (fs == null) {
                        fs = getFileStats(prefs, f);
                        cache.put(f, fs);
                    }
                    if (fs != null) {
                        long last = storage.getLast(f);
                        long size = storage.getLength(f);
                        if (last != fs.last || size != fs.size)
                            fs = null;
                    }
                    if (fs == null) {
                        fs = new FileStats();
                        fs.size = storage.getLength(f);
                        fs.last = storage.getLast(f);
                        try {
                            MediaPlayer mp = MediaPlayer.create(getContext(), f);
                            fs.duration = mp.getDuration();
                            cache.put(f, fs);
                            setFileStats(getContext(), f, fs);
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
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (t.isInterrupted())
                            return;
                        setNotifyOnChange(false);
                        clear();
                        Recordings.this.durations = durations;
                        TreeSet<String> delete = new TreeSet<>();
                        for (String k : prefs.keySet()) {
                            if (k.startsWith(MainApplication.PREFERENCE_DETAILS_PREFIX))
                                delete.add(k);
                        }
                        TreeSet<Uri> delete2 = new TreeSet<>(cache.keySet());
                        for (Uri f : all) {
                            if (toolbarFilterAll) {
                                add(f);
                            } else {
                                if (MainApplication.getStar(getContext(), f))
                                    add(f);
                            }
                            String p = MainApplication.getFilePref(f);
                            delete.remove(p + MainApplication.PREFERENCE_DETAILS_FS);
                            delete.remove(p + MainApplication.PREFERENCE_DETAILS_CONTACT);
                            delete.remove(p + MainApplication.PREFERENCE_DETAILS_STAR);
                            delete2.remove(f);
                        }
                        SharedPreferences.Editor editor = shared.edit();
                        for (String s : delete) {
                            editor.remove(s);
                        }
                        for (Uri f : delete2) {
                            cache.remove(f);
                        }
                        editor.commit();
                        sort();
                        notifyDataSetChanged();
                        if (done != null)
                            done.run();
                    }
                });
            }
        }, "Recordings Scan");
        thread.start();
    }

    public void sort() {
        Comparator<Uri> sort;
        if (toolbarSortName)
            sort = new SortName();
        else
            sort = new SortDate();
        sort(Collections.reverseOrder(sort));
    }

    public void close() {
        playerStop();
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    public void load(Runnable done) {
        Uri uri = storage.getStoragePath();
        scan(storage.scan(uri), done);
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

        final Uri f = getItem(position);

        final boolean starb = MainApplication.getStar(getContext(), f);
        final ImageView star = (ImageView) convertView.findViewById(R.id.recording_star);
        starUpdate(star, starb);
        star.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean b = !MainApplication.getStar(getContext(), f);
                MainApplication.setStar(getContext(), f, b);
                starUpdate(star, b);
            }
        });

        TextView title = (TextView) convertView.findViewById(R.id.recording_title);
        title.setText(storage.getDocumentName(f));

        TextView time = (TextView) convertView.findViewById(R.id.recording_time);
        time.setText(MainApplication.SIMPLE.format(new Date(storage.getLast(f))));

        TextView dur = (TextView) convertView.findViewById(R.id.recording_duration);
        dur.setText(MainApplication.formatDuration(getContext(), durations.get(f)));

        TextView size = (TextView) convertView.findViewById(R.id.recording_size);
        size.setText(MainApplication.formatSize(getContext(), storage.getLength(f)));

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
                builder.setMessage("...\\" + storage.getDocumentName(f) + "\n\n" + getContext().getString(R.string.are_you_sure));
                builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        playerStop();
                        dialog.cancel();
                        RemoveItemAnimation.apply(list, base, new Runnable() {
                            @Override
                            public void run() {
                                storage.delete(f);
                                view.setTag(TYPE_DELETED);
                                select(-1);
                                remove(f); // instant remove
                                load(null); // thread load
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
                e.setText(storage.getNameNoExt(f));
                e.setPositiveButton(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        playerStop();
                        String ext = storage.getExt(f);
                        String s = String.format("%s.%s", e.getText(), ext);
                        Uri ff = storage.rename(f, s);
                        boolean star = MainApplication.getStar(getContext(), f);
                        MainApplication.setStar(getContext(), ff, star); // copy star to new name
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
                    Uri u = f;

                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("audio/*");
                    intent.putExtra(Intent.EXTRA_EMAIL, "");
                    intent.putExtra(Intent.EXTRA_STREAM, u);
                    intent.putExtra(Intent.EXTRA_SUBJECT, storage.getDocumentName(f));
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

            KeyguardManager myKM = (KeyguardManager) getContext().getSystemService(Context.KEYGUARD_SERVICE);
            final boolean locked = myKM.inKeyguardRestrictedInputMode();

            ImageView trash = (ImageView) convertView.findViewById(R.id.recording_player_trash);
            if (locked) {
                trash.setOnClickListener(null);
                trash.setClickable(true);
                trash.setColorFilter(Color.GRAY);
            } else {
                trash.setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent));
                trash.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        delete.run();
                    }
                });
            }

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

    void starUpdate(ImageView star, boolean starb) {
        if (starb)
            star.setImageResource(R.drawable.ic_star_black_24dp);
        else
            star.setImageResource(R.drawable.ic_star_border_black_24dp);
    }

    void playerPlay(View v, Uri f) {
        if (player == null)
            player = MediaPlayer.create(getContext(), f);
        if (player == null) {
            Toast.makeText(getContext(), R.string.file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        player.start();

        updatePlayerRun(v, f);
    }

    void playerPause(View v, Uri f) {
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

    void updatePlayerRun(final View v, final Uri f) {
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

    boolean updatePlayerText(final View v, final Uri f) {
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

    AppCompatImageButton getCheckBox(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = getCheckBox(g.getChildAt(i));
                if (c != null) {
                    return (AppCompatImageButton) c;
                }
            }
        }
        if (v instanceof AppCompatImageButton) {
            return (AppCompatImageButton) v;
        }
        return null;
    }

    void selectToolbar(View v, boolean pressed) {
        AppCompatImageButton cc = getCheckBox(v);
        if (pressed) {
            int[] states = new int[]{
                    android.R.attr.state_checked,
            };
            cc.setImageState(states, false);
        } else {
            int[] states = new int[]{
                    -android.R.attr.state_checked,
            };
            cc.setImageState(states, false);
        }
    }

    void selectToolbar() {
        selectToolbar(toolbar_a, toolbarFilterAll);
        selectToolbar(toolbar_s, !toolbarFilterAll);
        selectToolbar(toolbar_n, toolbarSortName);
        selectToolbar(toolbar_d, !toolbarSortName);
    }

    public void setToolbar(ViewGroup v) {
        this.toolbar = v;
        toolbar_a = v.findViewById(R.id.toolbar_all);
        toolbar_a.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toolbarFilterAll = true;
                selectToolbar();
                load(null);
                save();
            }
        });
        toolbar_s = v.findViewById(R.id.toolbar_stars);
        toolbar_s.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toolbarFilterAll = false;
                selectToolbar();
                load(null);
                save();
            }
        });
        toolbar_n = v.findViewById(R.id.toolbar_name);
        toolbar_n.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toolbarSortName = true;
                sort();
                selectToolbar();
                save();
            }
        });
        toolbar_d = v.findViewById(R.id.toolbar_date);
        toolbar_d.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toolbarSortName = false;
                sort();
                selectToolbar();
                save();
            }
        });
        selectToolbar();
    }

    void save() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor edit = shared.edit();
        edit.putBoolean(MainApplication.PREFERENCE_SORT, toolbarSortName);
        edit.putBoolean(MainApplication.PREFERENCE_FILTER, toolbarFilterAll);
        edit.commit();
    }

    void load() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        toolbarSortName = shared.getBoolean(MainApplication.PREFERENCE_SORT, true);
        toolbarFilterAll = shared.getBoolean(MainApplication.PREFERENCE_FILTER, true);
    }
}
