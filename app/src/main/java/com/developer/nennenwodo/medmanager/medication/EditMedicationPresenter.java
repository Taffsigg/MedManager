package com.developer.nennenwodo.medmanager.medication;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.DatePicker;
import android.widget.TimePicker;

import com.developer.nennenwodo.medmanager.AlarmReceiver;
import com.developer.nennenwodo.medmanager.model.preferences.SharedPrefHelper;
import com.developer.nennenwodo.medmanager.model.sqlite.MedicationDBContract;
import com.developer.nennenwodo.medmanager.model.Medication;
import com.developer.nennenwodo.medmanager.model.sqlite.MedicationDBHelper;
import com.developer.nennenwodo.medmanager.model.preferences.SharedPrefContract;
import com.developer.nennenwodo.medmanager.utils.Utility;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;


/**
 * Responsible for handling actions from the view and updating the UI as required
 */
public class EditMedicationPresenter implements EditMedicationContract.Presenter{

    private EditMedicationContract.View mView;
    private Context mContext;


    public EditMedicationPresenter(EditMedicationContract.View view, Context context){
        mView = view;
        mContext = context;

    }


    /**
     * Gets medication having a particular id from the database and displays the medication in the appropriate fields
     * @param medicationID
     */
    @Override
    public void getAndDisplayMedication(int medicationID) {

        //init database helper
        MedicationDBHelper medicationDBHelper = new MedicationDBHelper(mContext);
        //retrieve from database
        Cursor cursor = medicationDBHelper.readSingleMedication(medicationID);

        cursor.moveToFirst();

        final int id = cursor.getInt(cursor.getColumnIndex(MedicationDBContract.MedicationEntry._ID));
        String name = cursor.getString(cursor.getColumnIndex(MedicationDBContract.MedicationEntry.NAME));
        String description = cursor.getString(cursor.getColumnIndex(MedicationDBContract.MedicationEntry.DESCRIPTION));
        int frequencyOrInterval = cursor.getInt(cursor.getColumnIndex(MedicationDBContract.MedicationEntry.INTERVAL));
        String startDate = cursor.getString(cursor.getColumnIndex(MedicationDBContract.MedicationEntry.START_DATE));
        String startTime = cursor.getString(cursor.getColumnIndex(MedicationDBContract.MedicationEntry.START_TIME));
        String endDate = cursor.getString(cursor.getColumnIndex(MedicationDBContract.MedicationEntry.END_DATE));

        cursor.close();

        //display data in appropriate views
        mView.displayMedicationName(name);
        mView.displayMedicationDescription(description);
        mView.displayMedicationFrequency(frequencyOrInterval);
        mView.displayMedicationStartDate(startDate);
        mView.displayMedicationStartTime(startTime);
        mView.displayMedicationEndDate(endDate);
    }

    /**
     * Performs actions when the edit medication has been clicked.
     * @param context
     * @param medicationID
     * @param medicationName
     * @param medicationDescription
     * @param medicationStartDate
     * @param medicationStartTime
     * @param medicationEndDate
     * @param medicationFrequency
     */
    @Override
    public void editMedicationClick(Context context, int medicationID, String medicationName, String medicationDescription, String medicationStartDate, String medicationStartTime, String medicationEndDate, String medicationFrequency) {

        if(medicationName.isEmpty() || medicationDescription.isEmpty() ||
                medicationStartDate.isEmpty() || medicationStartTime.isEmpty() ||
                medicationEndDate.isEmpty() || medicationFrequency.isEmpty()
                ){
            //make sure no field is empty
            mView.displayEmptyInputFieldMessage();
            return;
        }

        //if user enters an earlier date for medication end date, show toast error message
        if(! Utility.isBefore(medicationStartDate, medicationEndDate)){
            mView.displayInvalidDateMessage();
            return;
        }

        int medicationFrequencyOrInterval = 24; //default is set to every 24 hours (once a day).

        try{
            medicationFrequencyOrInterval = Integer.parseInt(medicationFrequency);

        }catch (Exception ex){
            mView.displayInvalidFrequencyErrorMessage();

        }

        //get user in current session using shared preferences
        SharedPrefHelper mSharedPrefHelper = new SharedPrefHelper(mContext);
        String userID = mSharedPrefHelper.getUserID();

        //create new medication instance
        Medication medication = new Medication(userID, medicationName, medicationDescription, medicationFrequencyOrInterval, medicationStartDate, medicationStartTime, medicationEndDate );

        //call to db to update medication
        MedicationDBHelper medicationDBHelper = new MedicationDBHelper(mContext);
        medicationDBHelper.updateMedication(medicationID,medication);

        //close the connection
        medicationDBHelper.close();

        Calendar calStartDate = Calendar.getInstance();
        calStartDate.set(Calendar.YEAR, Utility.getYear(medicationStartDate));
        calStartDate.set(Calendar.MONTH, Utility.getMonth(medicationStartDate));
        calStartDate.set(Calendar.DAY_OF_MONTH, Utility.getDay(medicationStartDate));
        calStartDate.set(Calendar.HOUR_OF_DAY, Utility.getHour(medicationStartTime));
        calStartDate.set(Calendar.MINUTE, Utility.getMinute(medicationStartTime));

        Calendar calEndDate = Calendar.getInstance();
        calEndDate.set(Calendar.YEAR, Utility.getYear(medicationEndDate));
        calEndDate.set(Calendar.MONTH, Utility.getMonth(medicationEndDate));
        calEndDate.set(Calendar.DAY_OF_MONTH, Utility.getDay(medicationEndDate));
        calEndDate.set(Calendar.HOUR_OF_DAY, Utility.getHour(medicationStartTime));
        calEndDate.set(Calendar.MINUTE, Utility.getMinute(medicationStartTime));

        int daysBetweenStartAndFinish = (int) Utility.daysBetween(medicationStartDate, medicationEndDate);

        int numberOfIterations = medicationFrequencyOrInterval * daysBetweenStartAndFinish;

        Date now = new Date();

        for(int i = 0; i < numberOfIterations; i++){

            Date dateTime = calStartDate.getTime();
            if(dateTime.before(now)){
                calStartDate.add(Calendar.HOUR, 24/medicationFrequencyOrInterval);
            }else{
                int year = calStartDate.get(Calendar.YEAR);
                int month = calStartDate.get(Calendar.MONTH) + 1;
                int day = calStartDate.get(Calendar.DAY_OF_MONTH);

                String formattedStartDate = Utility.formatDate(day,month,year);

                //add alarm
                //setAlarm(formattedStartDate, medicationEndDate, medicationStartTime, mContext, rowId, medicationFrequencyOrInterval);
                setAlarm(calStartDate, mContext, medicationID, medicationFrequencyOrInterval);

                break;
            }
        }

        mView.displayMedicationUpdateSuccessMessage();

    }

    /**
     * sets an alarm for the users new medication
     */
    private void setAlarm(Calendar start, Context ctx, long id, int frequency){

        Intent intent = new Intent(ctx,AlarmReceiver.class);
        intent.putExtra("MEDICATION_ID", (int)id);
        intent.setAction(""+System.currentTimeMillis());

        PendingIntent mPendingIntent = PendingIntent.getBroadcast(ctx, (int) id, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager alarmManager = (AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, start.getTimeInMillis(), (AlarmManager.INTERVAL_DAY / frequency), mPendingIntent);

    }

    /**
     * Shows datepicker dialog and displays chosen date
     */
    @Override
    public void medicationStartDayClick() {

        Calendar mCalendar = Calendar.getInstance();
        int year = mCalendar.get(Calendar.YEAR);
        int month = mCalendar.get(Calendar.MONTH);
        int day = mCalendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog startDatePickerDialog = new DatePickerDialog(mContext,
                android.R.style.Theme_Holo_Dialog,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker datePicker, int year, int month, int day) {
                        month = month + 1;

                        String startDate = Utility.formatDate(day,month,year);

                        mView.displayMedicationStartDate(startDate);

                    }
                }, year, month, day);

        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy");
        Date d = null;
        try {
            d = f.parse(day+"/"+month+"/"+year);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        if(d != null) {
            startDatePickerDialog.getDatePicker().setMinDate(d.getTime());
        }

        startDatePickerDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        startDatePickerDialog.show();

    }

    /**
     *Shows datepicker dialog and displays chosen date
     */
    @Override
    public void medicationEndDayClick() {

        Calendar mCalendar = Calendar.getInstance();
        int year = mCalendar.get(Calendar.YEAR);
        int month = mCalendar.get(Calendar.MONTH) + 1;
        int day = mCalendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog endDatePickerDialog = new DatePickerDialog(mContext,
                android.R.style.Theme_Holo_Dialog,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker datePicker, int year, int month, int day) {
                        month = month + 1;

                        String endDate = Utility.formatDate(day,month,year);

                        mView.displayMedicationEndDate(endDate);
                    }
                }, year, month, day);


        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy");
        Date d = null;
        try {
            d = f.parse(day+"/"+month+"/"+year);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        if(d != null) {
            endDatePickerDialog.getDatePicker().setMinDate(d.getTime());
        }
        endDatePickerDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        endDatePickerDialog.show();

    }

    /**
     * Shows timepicker dialog and displays chosen time
     */
    @Override
    public void medicationStartTimeClick() {

        Calendar mCalendar = Calendar.getInstance();

        TimePickerDialog startTimePickerDialog = new TimePickerDialog(mContext, new TimePickerDialog.OnTimeSetListener(){

            @Override
            public void onTimeSet(TimePicker timePicker, int hourOfDay, int minute) {

                String startTime = Utility.formatTime(hourOfDay, minute);
                mView.displayMedicationStartTime(startTime);

            }
        },
                mCalendar.get(Calendar.HOUR_OF_DAY), mCalendar.get(Calendar.MINUTE),
                true);

        startTimePickerDialog.show();
    }
}