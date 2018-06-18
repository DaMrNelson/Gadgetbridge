/*  Copyright (C) 2016-2018 Alberto, Andreas Shimokawa, Carsten Pfeiffer,
    Daniele Gobbetti

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.ImportExportSharedPreferences;


public class DbManagementActivity extends AbstractGBActivity {
    private static final Logger LOG = LoggerFactory.getLogger(DbManagementActivity.class);
    private static SharedPreferences sharedPrefs;
    private ImportExportSharedPreferences shared_file = new ImportExportSharedPreferences();

    private Button pushDBButton;
    private Button exportDBButton;
    private Button importDBButton;
    private Button deleteOldActivityDBButton;
    private Button deleteDBButton;
    private TextView dbPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_db_management);

        dbPath = (TextView) findViewById(R.id.activity_db_management_path);
        dbPath.setText(getExternalPath());

        pushDBButton = (Button) findViewById(R.id.pushDBButton);
        pushDBButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pushDBStep1();
            }
        });
        exportDBButton = (Button) findViewById(R.id.exportDBButton);
        exportDBButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportDB();
            }
        });
        importDBButton = (Button) findViewById(R.id.importDBButton);
        importDBButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importDB();
            }
        });

        int oldDBVisibility = hasOldActivityDatabase() ? View.VISIBLE : View.GONE;

        deleteOldActivityDBButton = (Button) findViewById(R.id.deleteOldActivityDB);
        deleteOldActivityDBButton.setVisibility(oldDBVisibility);
        deleteOldActivityDBButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteOldActivityDbFile();
            }
        });

        deleteDBButton = (Button) findViewById(R.id.emptyDBButton);
        deleteDBButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteActivityDatabase();
            }
        });

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    private boolean hasOldActivityDatabase() {
        return new DBHelper(this).existsDB("ActivityDatabase");
    }

    private String getExternalPath() {
        try {
            return FileUtils.getExternalFilesDir().getAbsolutePath();
        } catch (Exception ex) {
            LOG.warn("Unable to get external files dir", ex);
        }
        return getString(R.string.dbmanagementactivvity_cannot_access_export_path);
    }

    private void exportShared() {
        // BEGIN EXAMPLE
        File myPath = null;
        try {
            myPath = FileUtils.getExternalFilesDir();
            File myFile = new File(myPath, "Export_preference");
            shared_file.exportToFile(sharedPrefs,myFile,null);
        } catch (IOException ex) {
            GB.toast(this, getString(R.string.dbmanagementactivity_error_exporting_shared, ex.getMessage()), Toast.LENGTH_LONG, GB.ERROR, ex);
        }
    }

    private void importShared() {
        // BEGIN EXAMPLE
        File myPath = null;
        try {
            myPath = FileUtils.getExternalFilesDir();
            File myFile = new File(myPath, "Export_preference");
            shared_file.importFromFile(sharedPrefs,myFile );
        } catch (Exception ex) {
            GB.toast(DbManagementActivity.this, getString(R.string.dbmanagementactivity_error_importing_db, ex.getMessage()), Toast.LENGTH_LONG, GB.ERROR, ex);
        }
    }

    private void exportDB() {
        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            exportShared();
            DBHelper helper = new DBHelper(this);
            File dir = FileUtils.getExternalFilesDir();
            File destFile = helper.exportDB(dbHandler, dir);
            GB.toast(this, getString(R.string.dbmanagementactivity_exported_to, destFile.getAbsolutePath()), Toast.LENGTH_LONG, GB.INFO);
        } catch (Exception ex) {
            GB.toast(this, getString(R.string.dbmanagementactivity_error_exporting_db, ex.getMessage()), Toast.LENGTH_LONG, GB.ERROR, ex);
        }
    }

    private void pushDBStep1() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter the URL");

        // Set up the input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText("http://10.20.31.140:8080/db-upload");
        builder.setView(input);

        // Set up buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                String text = input.getText().toString();
                pushDBStep2(text);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });

        // Show
        builder.show();
    }

    private void pushDBStep2(String path) {
        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            // Export to a string
            exportShared(); // I think this just updates shared preferences
            DBHelper helper = new DBHelper(this);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            helper.exportDB(dbHandler, out);
            out.close();
            new DBPostTask(path, this).execute(out.toByteArray());
        } catch (Exception ex) {
            ex.printStackTrace();
            GB.toast(this, "Failed to get data for push", Toast.LENGTH_LONG, GB.ERROR, ex);
        }
    }

    private void importDB() {
        new AlertDialog.Builder(this)
                .setCancelable(true)
                .setTitle(R.string.dbmanagementactivity_import_data_title)
                .setMessage(R.string.dbmanagementactivity_overwrite_database_confirmation)
                .setPositiveButton(R.string.dbmanagementactivity_overwrite, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try (DBHandler dbHandler = GBApplication.acquireDB()) {
                            importShared();
                            DBHelper helper = new DBHelper(DbManagementActivity.this);
                            File dir = FileUtils.getExternalFilesDir();
                            SQLiteOpenHelper sqLiteOpenHelper = dbHandler.getHelper();
                            File sourceFile = new File(dir, sqLiteOpenHelper.getDatabaseName());
                            helper.importDB(dbHandler, sourceFile);
                            helper.validateDB(sqLiteOpenHelper);
                            GB.toast(DbManagementActivity.this, getString(R.string.dbmanagementactivity_import_successful), Toast.LENGTH_LONG, GB.INFO);
                        } catch (Exception ex) {
                            GB.toast(DbManagementActivity.this, getString(R.string.dbmanagementactivity_error_importing_db, ex.getMessage()), Toast.LENGTH_LONG, GB.ERROR, ex);
                        }
                    }
                })
                .setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
    }

    private void deleteActivityDatabase() {
        new AlertDialog.Builder(this)
                .setCancelable(true)
                .setTitle(R.string.dbmanagementactivity_delete_activity_data_title)
                .setMessage(R.string.dbmanagementactivity_really_delete_entire_db)
                .setPositiveButton(R.string.Delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (GBApplication.deleteActivityDatabase(DbManagementActivity.this)) {
                            GB.toast(DbManagementActivity.this, getString(R.string.dbmanagementactivity_database_successfully_deleted), Toast.LENGTH_SHORT, GB.INFO);
                        } else {
                            GB.toast(DbManagementActivity.this, getString(R.string.dbmanagementactivity_db_deletion_failed), Toast.LENGTH_SHORT, GB.INFO);
                        }
                    }
                })
                .setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
    }

    private void deleteOldActivityDbFile() {
        new AlertDialog.Builder(this).setCancelable(true);
        new AlertDialog.Builder(this).setTitle(R.string.dbmanagementactivity_delete_old_activity_db);
        new AlertDialog.Builder(this).setMessage(R.string.dbmanagementactivity_delete_old_activitydb_confirmation);
        new AlertDialog.Builder(this).setPositiveButton(R.string.Delete, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (GBApplication.deleteOldActivityDatabase(DbManagementActivity.this)) {
                    GB.toast(DbManagementActivity.this, getString(R.string.dbmanagementactivity_old_activity_db_successfully_deleted), Toast.LENGTH_SHORT, GB.INFO);
                } else {
                    GB.toast(DbManagementActivity.this, getString(R.string.dbmanagementactivity_old_activity_db_deletion_failed), Toast.LENGTH_SHORT, GB.INFO);
                }
            }
        });
        new AlertDialog.Builder(this).setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        new AlertDialog.Builder(this).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class DBPostTask extends AsyncTask<byte[], Integer, Integer> {

        private String path;
        private Context mContext;

        public DBPostTask(String path, Context context) {
            this.path = path;
            mContext = context;
        }

        protected Integer doInBackground(byte[]... datas) {
            byte[] data = datas[0];

            try { // https://stackoverflow.com/questions/11766878/sending-files-using-post-with-httpurlconnection
                // Create connection
                URL url = new URL(path);
                HttpURLConnection client = (HttpURLConnection) url.openConnection();
                client.setRequestMethod("POST");
                client.setRequestProperty("Content-Type", "multipart/form-data;boundary=*****");
                client.setDoInput(true);
                client.setDoOutput(true);

                // Write POST data (wrapped as a file)
                OutputStream out = client.getOutputStream();
                out.write("--*****\r\n".getBytes());
                out.write((
                        "Content-Disposition: form-data; name=\\\"" +
                                "test" + "\";filename=\"" +
                                "test.sqlite" + "\"\r\n").getBytes());
                out.write("\r\n".getBytes());
                out.write(data); // The data
                out.write("\r\n".getBytes());
                out.write("--*****--\r\n".getBytes());
                out.flush();
                out.close();

                // Complete
                client.connect();

                if (client.getResponseCode() != 200) {
                    throw new RuntimeException("Non-200 response code.");
                }

                // Notify success
                GB.toast(mContext, "Database pushed", Toast.LENGTH_LONG, GB.INFO);
            } catch (Exception ex) {
                ex.printStackTrace();
                GB.toast(mContext, "Failed to push data", Toast.LENGTH_LONG, GB.ERROR, ex);
            }


            return 0;
        }

        protected void onProgressUpdate(Integer progress) {}
        protected void onPostExecute(Integer result) {}
    }
}
