package com.alsaeedcullivan.ourtrips;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.alsaeedcullivan.ourtrips.utils.Const;
import com.alsaeedcullivan.ourtrips.utils.SharedPreference;
import com.alsaeedcullivan.ourtrips.utils.Utilities;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    // widgets
    private EditText mUsernameEditText, mPasswordEditText;
    private Button signInButton, signUpButton;
    private ProgressBar mProgressBar;

    private FirebaseUser mUser;
    private String mEmail;
    private String mPassword;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(Const.TAG, "onCreate: login");

        // determine whether there is a verified user that is logged in
        FirebaseAuth auth = FirebaseAuth.getInstance();
        mUser = auth.getCurrentUser();

        if (mUser != null) Log.d(Const.TAG, "onCreate login user: -> " + mUser.getEmail());
        else Log.d(Const.TAG, "onCreate: login user -> " + mUser);

        // if there is a verified registered user logged in, go straight to main activity
        if (mUser != null && mUser.isEmailVerified() && new SharedPreference(this).getRegistered()) {
            // check to see if they have registered
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
        }

        setContentView(R.layout.activity_login);

        // set-up activity title
        setTitle(getString(R.string.title_activity_login));

        // get references to the progress bar
        mProgressBar = findViewById(R.id.loading);

        // get references to the buttons
        signInButton = findViewById(R.id.sign_in);
        signUpButton = findViewById(R.id.sign_up);

        // get references to the text widgets
        mUsernameEditText = findViewById(R.id.username);
        mPasswordEditText = findViewById(R.id.password);

        // set the OnClickListener for the sign in button
        signInButton.setOnClickListener(signInListener());

        // set the OnClickListener for the sign up button
        signUpButton.setOnClickListener(signUpListener());
    }

    // handle lifecycle //

    @Override
    protected void onResume() {
        super.onResume();
        // set errors to null
        mUsernameEditText.setError(null);
        mPasswordEditText.setError(null);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // get input email & password
        String inputEmail = mUsernameEditText.getText().toString();
        String inputPassword = mPasswordEditText.getText().toString();
        // save input email & password
        if (!inputEmail.equals("")) outState.putString(Const.USER_ID_KEY, inputEmail);
        if (!inputPassword.equals("")) outState.putString(Const.USER_PASSWORD_KEY, inputPassword);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // get input email & password if entered
        mUsernameEditText.setText(savedInstanceState.getString(Const.USER_ID_KEY));
        mPasswordEditText.setText(savedInstanceState.getString(Const.USER_PASSWORD_KEY));
    }

    //  ******************************* private helper methods ******************************* //

    /**
     * signIn
     * attempts to sign in a user with a given email and password
     *
     * @param email    the input email
     * @param password the input password
     */
    private void signIn(String email, String password) {
        mEmail = email;
        mPassword = password;
        Log.d(Const.TAG, "signIn: login");

        // attempt to sign the user in
        new SignInTask().execute();
    }

    /**
     * hideBar()
     * helper method that hides the progress bar
     */
    private void hideBar() {
        // hide progress bar & show buttons
        mProgressBar.setVisibility(View.GONE);
        signInButton.setVisibility(View.VISIBLE);
        signUpButton.setVisibility(View.VISIBLE);
    }

    // listeners

    private View.OnClickListener signInListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(Const.TAG, "onClick: sign in");

                // get input email & password
                String inputEmail = mUsernameEditText.getText().toString();
                String inputPassword = mPasswordEditText.getText().toString();

                // perform error checking on entries //

                View focusView = null;

                // either fields is invalid
                if (!Utilities.isValidEmail(inputEmail)) {
                    mUsernameEditText.setError(getString(R.string.invalid_username));
                    focusView = mUsernameEditText;
                }
                if (!Utilities.isValidPassword(inputPassword)) {
                    mPasswordEditText.setError(getString(R.string.invalid_password));
                    focusView = mPasswordEditText;
                }

                // either fields is empty
                if (inputEmail.length() == 0) {
                    mUsernameEditText.setError(getString(R.string.required_field));
                    focusView = mUsernameEditText;
                }
                if (inputPassword.length() == 0) {
                    mPasswordEditText.setError(getString(R.string.required_field));
                    focusView = mPasswordEditText;
                }

                // -> something is invalid
                if (focusView != null) {
                    focusView.requestFocus();
                    return;
                }

                // valid entries //

                // show progress bar & hide buttons
                mProgressBar.setVisibility(View.VISIBLE);
                signInButton.setVisibility(View.GONE);
                signUpButton.setVisibility(View.GONE);

                // attempt to sign in
                signIn(inputEmail, inputPassword);
            }
        };
    }
    private View.OnClickListener signUpListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(Const.TAG, "onClick: sign up");

                // proceed to RegisterActivity
                Intent intent = new Intent(LoginActivity.this, VerifyActivity.class);
                intent.putExtra(Const.SOURCE_TAG, Const.LOGIN_TAG);
                // add email to intent
                intent.putExtra(Const.USER_EMAIL_KEY, mUsernameEditText.getText().toString());
                // add password to intent
                intent.putExtra(Const.USER_PASSWORD_KEY, mPasswordEditText.getText().toString());
                startActivity(intent);
            }
        };
    }


    // ASYNC TASKS

    /**
     * SignInTask
     * signs a user in
     */
    private class SignInTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            if (mEmail == null || mPassword == null) return null;

            Log.d(Const.TAG, "doInBackground: login sign in task");

            // attempt to sign in with the given email and password
            FirebaseAuth.getInstance().signInWithEmailAndPassword(mEmail, mPassword)
                    .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                // get the user
                                mUser = FirebaseAuth.getInstance().getCurrentUser();
                                if (mUser == null || !mUser.isEmailVerified()) {
                                    // tell the user that the email has not been verified
                                    Toast t = Toast.makeText(LoginActivity.this,
                                            R.string.need_to_verify, Toast.LENGTH_LONG);
                                    t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                                            0, 0);
                                    t.show();
                                    // sign the user out
                                    FirebaseAuth.getInstance().signOut();
                                    hideBar();
                                    return;
                                }

                                // check to see if the user is registered
                                new RegisterCheckTask().execute();
                            } else {
                                // there is no account with this name
                                // inform user they are wrong
                                Toast message = Toast.makeText(LoginActivity.this,
                                        R.string.login_failed, Toast.LENGTH_SHORT);
                                message.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                                        0, 0);
                                message.show();

                                // hide the progress bar
                                hideBar();
                            }
                        }
                    });

            return null;
        }
    }

    /**
     * RegisterCheckTask
     * checks to see if a user is registered, if they are they may proceed to Main Activity
     */
    private class RegisterCheckTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            if (mUser == null) return null;

            // check to see if the user is registered
            FirebaseFirestore.getInstance()
                    .collection(Const.USERS_COLLECTION)
                    .document(mUser.getUid())
                    .get()
                    .continueWith(new Continuation<DocumentSnapshot, Object>() {
                        @Override
                        public Object then(@NonNull Task<DocumentSnapshot> task) {
                            DocumentSnapshot doc = task.getResult();
                            // if they are registered
                            if (doc != null && doc.exists()) {
                                // save that this user is registered
                                new SharedPreference(getApplicationContext()).setRegistered(true);

                                Log.d(Const.TAG, "then: user is registered login");

                                // the user can proceed to main activity
                                startActivity(new Intent(LoginActivity.this, MainActivity.class));

                                // hide the progress bar
                                hideBar();

                                Log.d(Const.TAG, "then: finish signed in");
                                // finish the activity
                                finish();
                            } else {
                                // tell the user that they have not registered
                                Toast t = Toast.makeText(LoginActivity.this,
                                        R.string.not_registered, Toast.LENGTH_LONG);
                                t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                                        0, 0);
                                t.show();

                                // set that they have not registered
                                new SharedPreference(getApplicationContext()).setRegistered(false);

                                // sign the user out
                                FirebaseAuth.getInstance().signOut();

                                // hide the progress bar
                                hideBar();
                            }
                            return doc;
                        }
                    });

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Log.d(Const.TAG, "onPostExecute: doneeeee async");
        }
    }
}
