package com.sec.android.iap.sample.helper;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.concurrent.RejectedExecutionException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.sec.android.iap.IAPConnector;
//import com.sec.android.iap.sample.R;
import com.sec.android.iap.sample.vo.ErrorVO;
import com.sec.android.iap.sample.vo.InBoxVO;
import com.sec.android.iap.sample.vo.ItemVO;
import com.sec.android.iap.sample.vo.PurchaseVO;
import com.sec.android.iap.sample.vo.VerificationVO;

public class SamsungIapHelper
{
    private static final String TAG  = SamsungIapHelper.class.getSimpleName();
    private static final int    HONEYCOMB_MR1                   = 12;
    private static final int    FLAG_INCLUDE_STOPPED_PACKAGES   = 32;

    // IAP Signature Hashcode
    // ========================================================================
    public static final int     IAP_SIGNATURE_HASHCODE          = 0x7a7eaf4b;
    // ========================================================================
    
    // IAP NAME
    // ========================================================================
    public static final String  IAP_PACKAGE_NAME = "com.sec.android.iap";
    public static final String  IAP_SERVICE_NAME = 
                                      "com.sec.android.iap.service.iapService";
    // ========================================================================

    // BILLING RESPONSE CODE
    // ========================================================================
    public static final int     IAP_RESPONSE_RESULT_OK          = 0;
    public static final int     IAP_RESPONSE_RESULT_UNAVAILABLE = 2;
    // ========================================================================

    // BUNDLE KEY
    // ========================================================================
    public static final String  KEY_NAME_THIRD_PARTY_NAME = "THIRD_PARTY_NAME";
    public static final String  KEY_NAME_STATUS_CODE      = "STATUS_CODE";
    public static final String  KEY_NAME_ERROR_STRING     = "ERROR_STRING";
    public static final String  KEY_NAME_IAP_UPGRADE_URL  = "IAP_UPGRADE_URL";
    public static final String  KEY_NAME_ITEM_GROUP_ID    = "ITEM_GROUP_ID";
    public static final String  KEY_NAME_ITEM_ID          = "ITEM_ID";
    public static final String  KEY_NAME_RESULT_LIST      = "RESULT_LIST";
    public static final String  KEY_NAME_RESULT_OBJECT    = "RESULT_OBJECT";
    // ========================================================================

    // ITEM TYPE
    // ========================================================================
    public static final String ITEM_TYPE_CONSUMABLE       = "00";
    public static final String ITEM_TYPE_NON_CONSUMABLE   = "01";
    public static final String ITEM_TYPE_SUBSCRIPTION     = "02";
    public static final String ITEM_TYPE_ALL              = "10";
    // ========================================================================

    // IAP ????????? onActivityResult ?????? ?????? ?????? ?????? ??????
    // define request code for IAPService.
    // ========================================================================
    public static final int   REQUEST_CODE_IS_IAP_PAYMENT            = 100;
    public static final int   REQUEST_CODE_IS_ACCOUNT_CERTIFICATION  = 101;
    // ========================================================================
    
    // 3rd party ??? ???????????? ?????? ??????
    // define status code passed to 3rd party application 
    // ========================================================================
    /** ??????????????? ?????? */
    final public static int IAP_ERROR_NONE                   = 0;
    
    /** ?????? ????????? ?????? */
    final public static int IAP_PAYMENT_IS_CANCELED          = 1;
    
    /** initialization ????????? ?????? ?????? */
    final public static int IAP_ERROR_INITIALIZATION         = -1000;
    
    /** IAP ?????????????????? ????????? */
    final public static int IAP_ERROR_NEED_APP_UPGRADE       = -1001;
    
    /** ?????? ???????????? */
    final public static int IAP_ERROR_COMMON                 = -1002;
    
    /** NON CONSUMABLE ???????????? ?????? */
    final public static int IAP_ERROR_ALREADY_PURCHASED      = -1003;
    
    /** ???????????? ????????? Bundle ??? ?????? ?????? */
    final public static int IAP_ERROR_WHILE_RUNNING          = -1004;
    
    /** ????????? ?????? ????????? ?????? ?????? */
    final public static int IAP_ERROR_PRODUCT_DOES_NOT_EXIST = -1005;
    
    /** ?????? ????????? ????????? ???????????? ??????????????? ??? ?????? ?????????
     *  ????????? ?????? ?????? ????????? ????????? ?????? */
    final public static int IAP_ERROR_CONFIRM_INBOX          = -1006;
    // ========================================================================

    // IAP??? ????????? ??????
    // ========================================================================
    /**???????????? ?????? ????????? ????????? ?????? ????????? ????????? ???????????????.
     * Developer mode for test. Always return successful result*/
    final public static int IAP_MODE_TEST_SUCCESS             =  1;
    
    /**???????????? ?????? ????????? ????????? ?????? ????????? ????????? ???????????????.
     * Developer mode for test. Always return failed result*/
    final public static int IAP_MODE_TEST_FAIL                = -1;
    
    /**?????? ????????? ?????????????????????.
     * Real service mode*/
    final public static int IAP_MODE_COMMERCIAL               =  0;
    
    /**
     * ??? ????????? IAP_MODE_COMMERCIAL??? ??????????????? ?????? ????????? ???????????????.
     *   ????????? ?????? ????????? ????????? ?????????.<BR>
     * ??? Mode must be set to IAP_MODE_COMMERCIAL, the real payment occurs.
     *   Please check this mode before release.<BR>
     */
    private int                   mMode = IAP_MODE_TEST_SUCCESS;
    // ========================================================================
    
    private Context               mContext         = null;
    private ProgressDialog        mProgressDialog  = null;

    private IAPConnector          mIapConnector    = null;
    private ServiceConnection     mServiceConn     = null;
    
    // AsyncTask for IAPService Initialization
    // ========================================================================
    private InitIapTask           mInitIapTask            = null;
    private OnInitIapListener     mOnInitIapListener      = null;
    // ========================================================================
    
    // AsyncTask for get item list
    // ========================================================================
    private GetItemListTask       mGetItemListTask        = null;
    private OnGetItemListListener mOnGetItemListListener  = null;
    // ========================================================================

    // AsyncTask for get inbox list
    // ========================================================================
    private GetInboxListTask       mGetInboxListTask       = null;
    private OnGetInboxListListener mOnGetInboxListListener = null;
    // ========================================================================

    // ?????? ????????? ??????
    // verify payment result by server
    // ========================================================================
    private VerifyClientToServer   mVerifyClientToServer   = null;
    // ========================================================================
    
    
    private static SamsungIapHelper mInstance = null;
    
    // IAP Service ??????
    // State of IAP Service
    // ========================================================================
    private int mState = STATE_TERM;
    
    /** initial state */
    private static final int STATE_TERM     = 0;
    
    /** state of bound to IAPService successfully */
    private static final int STATE_BINDING  = 1;
    
    /** state of InitIapTask successfully finished */
    private static final int STATE_READY    = 2; // 
    // ========================================================================
    
    public static SamsungIapHelper getInstance( Context _context, int _mode )
    {
        if( null == mInstance )
        {
            mInstance = new SamsungIapHelper( _context, _mode );
        }
        else
        {
            mInstance.setContextAndMode( _context, _mode );
        }
        
        return mInstance;
    }

    
    public void setContextAndMode( Context _context, int _mode )
    {
        mContext = _context.getApplicationContext();
        mMode    = _mode;        
    }
    
    /**
     * SamsungIapHelper ????????? Application??? Context??? ????????????.
     * constructor
     * @param _context
     */
    public SamsungIapHelper( Context _context , int _mode )
    {
        setContextAndMode( _context, _mode );
    }
    
    /**
     * IAP ????????? ?????? ??????
     * set of IAP service mode
     * @param _mode
     */
    public void setMode( int _mode )
    {
        mMode = _mode;
    }
    
    /**
     * IAP ????????? ???????????? ??????????????? ??? ???????????? ?????? ????????? ????????????.
     * Register a callback to be invoked
     * when {@link InitIapTask} has been finished.
     * @param _onInitIaplistener
     */
    public void setOnInitIapListener( OnInitIapListener _onInitIaplistener )
    {
        mOnInitIapListener = _onInitIaplistener;
    }
    
    /**
     * {@link GetItemListTask}??? ??????????????? ??? ???????????? ????????? ????????????.
     * Register a callback to be invoked
     * when {@link GetItemListTask} has been finished.
     * @param _onInitIaplistener
     */
    public void setOnGetItemListListener(
                                 OnGetItemListListener _onGetItemListListener )
    {
        mOnGetItemListListener = _onGetItemListListener;
    }
    
    /**
     * {@link GetInboxListTask}??? ??????????????? ??? ???????????? ????????? ????????????.
     * Register a callback to be invoked
     * when {@link GetInboxListTask} has been finished.
     * @param _onInitIaplistener
     */
    public void setOnGetInboxListListener( 
                               OnGetInboxListListener _onGetInboxListListener )
    {
        mOnGetInboxListListener = _onGetInboxListListener;
    }
    
    /**
     * SamsungAccount ??????
     * SamsungAccount Authentication
     * @param _activity
     */
    public void startAccountActivity( final Activity _activity )
    {
        ComponentName com = new ComponentName( "com.sec.android.iap", 
                              "com.sec.android.iap.activity.AccountActivity" );

        Intent intent = new Intent();
        intent.setComponent( com );

        _activity.startActivityForResult( intent,
                                       REQUEST_CODE_IS_ACCOUNT_CERTIFICATION );
    }
    
    /**
     * IAP ?????????????????? ??????
     * go to page of SamsungApps in order to install IAP
     */
    public void installIapPackage( final Activity _activity )
    {
        Runnable OkBtnRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                // SamsungApps??? IAP ??????
                // Link of SamsungApps for IAP install
                // ============================================================
                Uri iapDeepLink = Uri.parse( 
                           "samsungapps://ProductDetail/com.sec.android.iap" );
                // ============================================================
                
                Intent intent = new Intent();
                intent.setData( iapDeepLink );

                // ????????? MR1 ???????????? FLAG_INCLUDE_STOPPED_PACKAGES??? ????????????.
                // If android OS version is more HoneyComb MR1,
                // add flag FLAG_INCLUDE_STOPPED_PACKAGES
                // ============================================================
                if( Build.VERSION.SDK_INT >= HONEYCOMB_MR1 )
                {
                    intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK | 
                                     Intent.FLAG_ACTIVITY_CLEAR_TOP | 
                                     FLAG_INCLUDE_STOPPED_PACKAGES );
                }
                // ============================================================
                else
                {
                    intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK | 
                                     Intent.FLAG_ACTIVITY_CLEAR_TOP );
                }

                mContext.startActivity( intent );
            }
        };
        
        showIapDialog( _activity, 
                       "in_app_purchase",
                       "msg_iap_is_not_installed",
                       true, 
                       OkBtnRunnable );
    }
    
    /**
     * IAP ???????????? ???????????? ?????? ??? ?????? ??????
     * check that IAP package is installed
     * @param _context
     * @return
     */
    public boolean isInstalledIapPackage( Context _context )
    {
        PackageManager pm = _context.getPackageManager();
        
        try
        {
            pm.getApplicationInfo( IAP_PACKAGE_NAME,
                                   PackageManager.GET_META_DATA );
            return true;
        }
        catch( NameNotFoundException e )
        {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * ??????????????? ????????? IAP ???????????? ????????? ??????
     * check validation of installed IAP package in your device
     * @param _context
     * @return
     */
    public boolean isValidIapPackage( Context _context )
    {
        boolean result = true;
        
        try
        {
            Signature[] sigs = _context.getPackageManager().getPackageInfo(
                                    IAP_PACKAGE_NAME,
                                    PackageManager.GET_SIGNATURES ).signatures;
            
            if( sigs[0].hashCode() != IAP_SIGNATURE_HASHCODE )
            {
                result = false;
            }
        }
        catch( Exception e )
        {
            e.printStackTrace();
            result = false;
        }
        
        return result;
    }
    
    
    /**
     * IAP Interface??? ???????????? ????????? Service Bind ????????? ??????. 
     * ??????????????? Listener??? ????????????.
     * bind to IAPService
     *  
     * @param _listener The listener that receives notifications
     * when bindIapService method is finished.
     */
    public void bindIapService( final OnIapBindListener _listener )
    {
        // ?????? ???????????? ??????
        // exit If already bound 
        // ====================================================================
        if( mState >= STATE_BINDING )
        {
            if( _listener != null )
            {
                _listener.onBindIapFinished( IAP_RESPONSE_RESULT_OK );
            }
            
            return;
        }
        // ====================================================================

        // Connection to IAP service
        // ====================================================================
        mServiceConn = new ServiceConnection()
        {
            @Override
            public void onServiceDisconnected( ComponentName _name )
            {
                Log.d( TAG, "IAP Service Disconnected..." );

                mState        = STATE_TERM;
                mIapConnector = null;
                mServiceConn  = null;
            }

            @Override
            public void onServiceConnected
            (   
                ComponentName _name,
                IBinder       _service
            )
            {
                mIapConnector = IAPConnector.Stub.asInterface( _service );

                if( mIapConnector != null && _listener != null )
                {
                    mState = STATE_BINDING;
                    
                    _listener.onBindIapFinished( IAP_RESPONSE_RESULT_OK );
                }
                else
                {
                    mState = STATE_TERM;
                    
                    _listener.onBindIapFinished( 
                                             IAP_RESPONSE_RESULT_UNAVAILABLE );
                }
            }
        };
        // ====================================================================
        
        Intent serviceIntent = new Intent( IAP_SERVICE_NAME );
        
        // bind to IAPService
        // ====================================================================
        mContext.bindService( serviceIntent, 
                              mServiceConn,
                              Context.BIND_AUTO_CREATE );
        // ====================================================================
    }
    
    
    /**
     * IAP??? init Interface??? ???????????? IAP ????????? ????????? ????????????.
     * process IAP initialization by calling init() interface in IAPConnector
     * @return ErrorVO
     */
    public ErrorVO init()
    {
        ErrorVO errorVO = new ErrorVO();
        
        try
        {
            Bundle bundle = mIapConnector.init( mMode );
            
            if( null != bundle )
            {
                errorVO.setErrorCode( bundle.getInt( KEY_NAME_STATUS_CODE ) );
                
                errorVO.setErrorString( 
                                   KEY_NAME_ERROR_STRING );
                
                errorVO.setExtraString( 
                                 KEY_NAME_IAP_UPGRADE_URL );
            }
        }
        catch( RemoteException e )
        {
            e.printStackTrace();
        }
        
        return errorVO;
    }

    /**
     * IAP ?????? ?????? Interface ??? ???????????? ????????? ??????
     * load list of item by calling getItemList() method in IAPConnector
     * 
     * @param _itemGroupId
     * @param _startNum
     * @param _endNum
     * @param _itemType
     * @return Bundle
     */
    public Bundle getItemList
    (   
        String  _itemGroupId,
        int     _startNum,
        int     _endNum,
        String  _itemType
    )
    {
        Bundle itemList = null;
        
        try
        {
            itemList = mIapConnector.getItemList( mMode,
                                                  mContext.getPackageName(),
                                                  _itemGroupId,
                                                  _startNum,
                                                  _endNum,
                                                  _itemType );
        }
        catch( RemoteException e )
        {
            e.printStackTrace();
        }

        return itemList;
    }

    /**
     * IAP ????????? ?????? ??????  Interface ??? ???????????? ????????? ??????
     * call getItemsInbox() method in IAPConnector
     * to load List of purchased item
     * 
     * @param _itemGroupId  String
     * @param _startNum     int
     * @param _endNum       int
     * @param _startDate    String "yyyyMMdd" format
     * @param _endDate      String "yyyyMMdd" format
     * @return Bundle
     */
    public Bundle getItemsInbox
    (   
        String  _itemGroupId,
        int     _startNum,
        int     _endNum,
        String  _startDate,
        String  _endDate
    )
    {
        Bundle purchaseItemList = null;
        
        try
        {
            purchaseItemList = mIapConnector.getItemsInbox(
                                                     mContext.getPackageName(),
                                                     _itemGroupId,
                                                     _startNum, 
                                                     _endNum,
                                                     _startDate,
                                                     _endDate );
        }
        catch( RemoteException e )
        {
            e.printStackTrace();
        }

        return purchaseItemList;
    }

    
    /**
     * IAP??? ?????? Activity ??? ????????????.
     * call PaymentMethodListActivity in IAP in order to process payment
     * @param _activity
     * @param _requestCode
     * @param _itemGroupId
     * @param _itemId
     */
    public void startPurchase
    (   
        Activity  _activity,
        int       _requestCode,
        String    _itemGroupId,
        String    _itemId
    )
    {
        try
        {
            Bundle bundle = new Bundle();
            bundle.putString( KEY_NAME_THIRD_PARTY_NAME,
                              mContext.getPackageName() );
            
            bundle.putString( KEY_NAME_ITEM_GROUP_ID, _itemGroupId );
            
            bundle.putString( KEY_NAME_ITEM_ID, _itemId );
            
            ComponentName com = new ComponentName( "com.sec.android.iap", 
                    "com.sec.android.iap.activity.PaymentMethodListActivity" );

            Intent intent = new Intent( Intent.ACTION_MAIN );
            intent.addCategory( Intent.CATEGORY_LAUNCHER );
            intent.setComponent( com );

            intent.putExtras( bundle );

            _activity.startActivityForResult( intent, _requestCode );
        }
        catch( Exception e )
        {
            e.printStackTrace();
        }
    }

    
    /**
     * Dialog ??? ????????????.
     * show dialog
     * @param _title
     * @param _message
     */
    public void showIapDialog
    ( 
        final Activity _activity,
        String         _title, 
        String         _message,
        final boolean  _finishActivity,
        final Runnable _onClickRunable 
    )
    {
        AlertDialog.Builder alert = new AlertDialog.Builder( _activity );
        
        alert.setTitle( _title );
        alert.setMessage( _message );
        
        alert.setPositiveButton( "OK", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick( DialogInterface _dialog, int _which )
            {
                if( null != _onClickRunable )
                {
                    _onClickRunable.run();
                }
                
                _dialog.dismiss();
                
                if( true == _finishActivity )
                {
                    _activity.finish();
                }
            }
        } );
        
        if( true == _finishActivity )
        {
            alert.setOnCancelListener( new DialogInterface.OnCancelListener()
            {
                @Override
                public void onCancel( DialogInterface dialog )
                {
                    _activity.finish();
                }
            });
        }
            
        alert.show();
    }
    
    /**
     * ???????????? ???????????? ????????????.
     * Stop running task
     */
    public void stopRunningTask()
    {
        if( mInitIapTask != null )
        {
            if( mInitIapTask.getStatus() != Status.FINISHED )
            {
                mInitIapTask.cancel( true );
            }
        }
        
        if( mGetItemListTask != null )
        {
            if ( mGetItemListTask.getStatus() != Status.FINISHED )
            {
                mGetItemListTask.cancel( true );
            }
        }
        
        if( mGetInboxListTask != null )
        {
            if( mGetInboxListTask.getStatus() != Status.FINISHED )
            {
                mGetInboxListTask.cancel( true );
            }
        }         
        
        if( mVerifyClientToServer != null )
        {
            if( mVerifyClientToServer.getStatus() != Status.FINISHED )
            {
                mVerifyClientToServer.cancel( true );
            }
        }
    }
    
    /**
     * IAP ????????? ???????????? Connecter ????????? Service??? unbind ?????????.
     * unbind from IAPService when you are done with activity. 
     */
    public void dispose()
    {
        if( mContext != null && mServiceConn != null )
        {
            mContext.unbindService( mServiceConn );
        }
        
        mState         = STATE_TERM;
        mServiceConn   = null;
        mIapConnector  = null;
    }

    /**
     * ProgressDialog ??? ????????????.
     * show progress dialog
     * @param _context
     */
    public void showProgressDialog( Context _context )
    {
        if( mProgressDialog == null || false == mProgressDialog.isShowing() )
        {
            mProgressDialog = ProgressDialog.show(
                                    _context,
                                    "",
                                     "waiting_ing" ,
                                    true );
        }
    }

    /**
     * ProgressDialog ??? ?????????.
     * dismiss progress dialog
     * @param progressDialog
     */
    public void dismissProgressDialog()
    {
        try
        {
            if( null != mProgressDialog && mProgressDialog.isShowing() )
            {
                mProgressDialog.dismiss();
            }
        }
        catch( Exception e )
        {
            e.printStackTrace();
        }
    }
    
    /**
     * bindIapService ???????????? ??????????????? ??? ???????????? ??????????????? ??????
     * Interface definition for a callback to be invoked
     * when bindIapService method has been finished.
     */
    public interface OnIapBindListener
    {
        /**
         * bindIapService ???????????? ??????????????? ??? ???????????? ?????? ?????????
         * Callback method to be invoked
         * when bindIapService() method has been finished.
         * @param result
         */
        public void onBindIapFinished( int result );
    }
    
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////// InitIapTask /////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    /**
     * IAP ????????? ???????????? ??????????????? ??? ???????????? ??????????????? ?????? ??????????????? ??????
     * Interface definition for a callback to be invoked
     * when {@link InitIapTask} has been finished.
     */
    public interface OnInitIapListener
    {
        /**
         * Callback method to be invoked 
         * when {@link InitIapTask} has been finished successfully. 
         */
        public void onSucceedInitIap();
    }
    
    /**
     * execute {@link InitIapTask}
     */
    public void safeInitIap( Activity _activity )
    {
        try
        {
            if( mInitIapTask != null &&
                mInitIapTask.getStatus() != Status.FINISHED )
            {
                mInitIapTask.cancel( true );
            }

            mInitIapTask = new InitIapTask( _activity );
            mInitIapTask.execute();
        }
        catch( RejectedExecutionException e )
        {
            Log.e( TAG, "safeInitTask()\n" + e.toString() );
        }
        catch( Exception e )
        {
            e.printStackTrace();
            Log.e( TAG, "safeInitTask()\n" + e.toString() );
        }
    }
    
    /**
     * IAP??? ??????????????? AsyncTask
     * AsyncTask for initializing of IAPService
     */
    private class InitIapTask  extends AsyncTask<String, Object, Boolean>
    {
        private Activity       mActivity       = null;
        private ErrorVO        mErrorVO        = new ErrorVO();
        
        public InitIapTask( Activity _activity )
        {
            mActivity = _activity;
        }
        
        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            
            if( null == mOnInitIapListener || null == mActivity )
            {
                cancel( true );
            }

            showProgressDialog( mActivity );
        }
        
        
        @Override
        protected void onCancelled()
        {
            dismissProgressDialog();
            super.onCancelled();
        }

        @Override
        protected Boolean doInBackground( String... params )
        {
            try
            {
                // IAP ????????? Interface ??? ???????????? ????????? ????????????.
                // Initialize IAPService by calling init() method of IAPService
                // ============================================================
                if( mState == STATE_READY )
                {
                    mErrorVO.setErrorCode( IAP_ERROR_NONE );
                }
                else
                {
                    mErrorVO = init();
                }
                // ============================================================
                
                return true;
            }
            catch( Exception e )
            {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute( Boolean result )
        {
            // ???????????? : InitTask ????????? ????????? ??????
            // InitTask returned true
            // ================================================================
            if( true == result )
            {
                // 1) ???????????? ??????????????? ????????? ??????
                //    If initialization is completed successfully
                // ============================================================
                if( mErrorVO.getErrorCode() == IAP_ERROR_NONE )
                {
                    // ???????????? ???????????? ?????? Listener ??? onSucceedInitIap 
                    // ?????? ????????? ??????
                    // Callback method call
                    // --------------------------------------------------------
                    if( null != mOnInitIapListener )
                    {
                        mState = STATE_READY;
                        mOnInitIapListener.onSucceedInitIap();
                    }
                    // --------------------------------------------------------
                }
                // ============================================================
                // 2) IAP ???????????? ?????????????????? ????????? ??????
                //    If the IAP package needs to be upgraded
                // ============================================================
                else if( mErrorVO.getErrorCode() == IAP_ERROR_NEED_APP_UPGRADE )
                {
                    dismissProgressDialog();
                    
                    Runnable OkBtnRunnable = new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            if( true == TextUtils.isEmpty( 
                                                  mErrorVO.getExtraString() ) )
                            {
                                return;
                            }
                            
                            Intent intent = new Intent();
                            
                            intent.setData( 
                                      Uri.parse( mErrorVO.getExtraString() ) );
                            
                            intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );

                            try
                            {
                                mActivity.startActivity( intent );
                            }
                            catch( ActivityNotFoundException e )
                            {
                                e.printStackTrace();
                            }
                        }
                    };
                    
                    showIapDialog(
                            mActivity,
                            "in_app_purchase" , 
                            "msg_iap_upgrade_is_required"  +
                                "\n\n" + mErrorVO.getErrorString(),
                            true,
                            OkBtnRunnable );
                
                    Log.e( TAG, mErrorVO.getErrorString() );
                }
                // ============================================================
                // 3) IAP ????????? ?????? ???????????? ????????? ??????
                //    If the IAPService failed to initialize
                // ============================================================
                else
                {
                    dismissProgressDialog();
                    
                    showIapDialog(
                           mActivity,
                           "in_app_purchase" , 
                            "msg_failed_to_initialize_iap"  +
                               "\n\n" + mErrorVO.getErrorString(), 
                           false,
                           null );
                
                    Log.e( TAG, mErrorVO.getErrorString() );
                }
                // ============================================================
            }
            // ================================================================

            // 3. InitIAPTask returned false
            // ================================================================
            else
            {
                dismissProgressDialog();
                
                showIapDialog(
                        mActivity,
                        "in_app_purchase" , 
                         "msg_failed_to_initialize_iap" ,
                        false,
                        null );
                
                Log.e( TAG,  mErrorVO.getErrorString() );
            }
            // ================================================================
        }
    }
    
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    /////////////////////////// GetItemListTask ///////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    /**
     * execute GetItemListTask
     */
    public void safeGetItemList
    (   
        Activity _activity,
        String   _itemGroupId,
        int      _startNum,
        int      _endNum,
        String   _itemType
    )
    {
        try
        {
            if( mGetItemListTask != null &&
                mGetItemListTask.getStatus() != Status.FINISHED )
            {
                mGetItemListTask.cancel( true );
            }

            mGetItemListTask = new GetItemListTask( _activity,
                                                    _itemGroupId,
                                                    _startNum,
                                                    _endNum,
                                                    _itemType );
            mGetItemListTask.execute();
        }
        catch( RejectedExecutionException e )
        {
            e.printStackTrace();
        }
        catch( Exception e )
        {
            e.printStackTrace();
        }
    }

    /**
     * {@link GetItemListTask}??? ??????????????? ??? ???????????? ?????????????????? ?????? ???????????????
     * Interface definition for a callback to be invoked
     * when {@link GetItemListTask} has been finished.
     */
    public interface OnGetItemListListener
    {
        /**
         * Callback method to be invoked 
         * when {@link GetItemListTask} has been succeeded. 
         */
        public void onSucceedGetItemList( ArrayList<ItemVO>   _itemList );
    }
    
    /**
     * AsyncTask to load a list of items
     */
    private class GetItemListTask extends AsyncTask<String, Object, Boolean>
    {
        private ArrayList<ItemVO> mMoreItemVOList   = null;
        private ErrorVO           mErrorVO          = new ErrorVO();
        private String            mItemGroupId      = "";
        private int               mStartNum         = 1;
        private int               mEndNum           = 15;
        private String            mItemType         = "";
        
        private Activity          mActivity         = null;

        public GetItemListTask
        (
            Activity  _activity,
            String    _itemGroupId,
            int       _startNum,
            int       _endNum,
            String    _itemType
        )
        {
            mActivity    = _activity;
            mItemGroupId = _itemGroupId;
            mStartNum    = _startNum;
            mEndNum      = _endNum;
            mItemType    = _itemType; 
        }
        
        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            
            if( mActivity == null && mOnGetItemListListener == null )
            {
                cancel( true );
            }
            
            mMoreItemVOList = new ArrayList<ItemVO>();
            showProgressDialog( mActivity );
        }

        @Override
        protected void onCancelled()
        {
            dismissProgressDialog();
            super.onCancelled();
        }
        
        @Override
        protected Boolean doInBackground( String... params )
        {
            try
            {
                // IAP ?????? ?????? Interface ??? ???????????? ????????? ????????????.
                // call getItemList() method of IAPService
                // ============================================================
                Bundle bundle = getItemList( mItemGroupId,
                                             mStartNum,
                                             mEndNum,
                                             mItemType );
                // ============================================================
                
                // status Code, error String, extra String ??? ???????????????
                // save status code, error string android extra String.
                // ============================================================
                mErrorVO.setErrorCode( bundle.getInt( KEY_NAME_STATUS_CODE ) );
                
                mErrorVO.setErrorString( bundle.getString( 
                                                     KEY_NAME_ERROR_STRING ) );
                
                mErrorVO.setExtraString( bundle.getString( 
                                                  KEY_NAME_IAP_UPGRADE_URL ) );
                // ============================================================
                
                if( mErrorVO.getErrorCode() == IAP_ERROR_NONE )
                {
                    ArrayList<String> itemStringList = 
                             bundle.getStringArrayList( KEY_NAME_RESULT_LIST );
                    
                    if( itemStringList != null )
                    {
                        for( String itemString : itemStringList )
                        {
                            ItemVO itemVO = new ItemVO( itemString );
                            
                            // TODO ?????? ??????
                            // ------------------------------------------------
                            Log.i( TAG, "S================================>" );
                            Log.i( TAG, itemVO.dump() );
                            Log.i( TAG, "E================================>" );
                            // ------------------------------------------------
                            
                            mMoreItemVOList.add( itemVO );
                        }
                    }
                    else
                    {
                        Log.d( TAG, "RESULT_LIST of bundle is empty.\n" );
                    }
                }
                else
                {
                    Log.d( TAG, mErrorVO.getErrorString() );
                }
            }
            catch( Exception e )
            {
                e.printStackTrace();
                return false;
            }
            
            return true;
        }

        @Override
        protected void onPostExecute( Boolean _result )
        {
            // 1. ????????? ????????? ??????
            //    result is true
            // ================================================================
            if( true == _result )
            {
                // 1) ?????? ???????????? ??????????????? ??????????????? ??????
                //    If list of product is successfully loaded
                // ============================================================
                if( mErrorVO.getErrorCode() == IAP_ERROR_NONE )
                {
                    // Callback method call
                    // --------------------------------------------------------
                    if( null != mOnGetItemListListener )
                    {
                        mOnGetItemListListener.onSucceedGetItemList( 
                                                             mMoreItemVOList );
                    }
                    // --------------------------------------------------------
                }
                // ============================================================
                // 2) IAP ???????????? ?????????????????? ????????? ??????
                //    If the IAP package needs to be upgraded
                // ============================================================
                else if( mErrorVO.getErrorCode() == IAP_ERROR_NEED_APP_UPGRADE )
                {
                    dismissProgressDialog();
                    
                    Runnable OkBtnRunnable = new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            if( true == TextUtils.isEmpty( 
                                                  mErrorVO.getExtraString() ) )
                            {
                                return;
                            }
                            
                            Intent intent = new Intent();
                            
                            intent.setData( 
                                      Uri.parse( mErrorVO.getExtraString() ) );
                            
                            intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );

                            try
                            {
                                mActivity.startActivity( intent );
                            }
                            catch( ActivityNotFoundException e )
                            {
                                e.printStackTrace();
                            }
                        }
                    };
                    
                    showIapDialog(
                            mActivity,
                            "in_app_purchase" , 
                            "msg_iap_upgrade_is_required"  +
                                "\n\n" + mErrorVO.getErrorString(),
                            true,
                            OkBtnRunnable );
                
                    Log.e( TAG, mErrorVO.getErrorString() );
                }
                // ============================================================
                // 3) ?????? ???????????? ???????????? ?????? ????????? ????????? ??????
                //    If error is occurred during loading list of product
                // ============================================================
                else
                {
                    dismissProgressDialog();
                    
                    showIapDialog(
                           mActivity,
                           "in_app_purchase" , 
                           "msg_failed_to_load_list_of_product"  +
                               "\n\n" + mErrorVO.getErrorString(), 
                           false,
                           null );
                
                    Log.e( TAG, mErrorVO.getErrorString() );
                }
                // ============================================================
            }
            // ================================================================
            // 2. result is false
            // ================================================================
            else
            {
                dismissProgressDialog();
                
                showIapDialog(
                        mActivity,
                        "in_app_purchase ", 
                        "msg_failed_to_load_list_of_product ",
                        false,
                        null );
            }
            // ================================================================
        }
    }
    
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////// GetInBoxListTask ////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    /**
     * execute GetItemInboxTask
     */
    public void safeGetItemInboxTask
    (
        Activity    _activity,
        String      _itemGroupId,
        int         _startNum,
        int         _endNum,
        String      _startDate,
        String      _endDate
    )
    {
        try
        {
            if( mGetInboxListTask != null &&
                mGetInboxListTask.getStatus() != Status.FINISHED )
            {
                mGetInboxListTask.cancel( true );
            }

            mGetInboxListTask = new GetInboxListTask( _activity,
                                                      _itemGroupId,
                                                      _startNum,
                                                      _endNum,
                                                      _startDate,
                                                      _endDate );
            mGetInboxListTask.execute();
        }
        catch( RejectedExecutionException e )
        {
            e.printStackTrace();
        }
        catch( Exception e )
        {
            e.printStackTrace();
        }
    }
    
    /**
     * {@link GetInboxListTask}??? ??????????????? ??? ???????????? ?????????????????? ?????? ???????????????
     * Interface definition for a callback to be invoked
     * when {@link GetInboxListTask} has been finished.
     */
    public interface OnGetInboxListListener
    {
        /**
         * Callback method to be invoked 
         * when {@link GetInboxListTask} has been succeeded. 
         */
        public void OnSucceedGetInboxList( ArrayList<InBoxVO>  _inboxList );
    }
    
    /**
     * AsyncTask to load a list of purchased items
     */
    private class GetInboxListTask extends AsyncTask<String, Object, Boolean>
    {
        private Activity            mActivity           = null;
        private String              mItemGroupId        = "";
        private int                 mStartNum           = 0;
        private int                 mEndNum             = 15;
        private String              mStartDate          = "20130101";
        private String              mEndDate            = "20130729";
        
        private ErrorVO             mErrorVO            = new ErrorVO();
        private ArrayList<InBoxVO>  mMoreInboxVOList    = null;
        
        public GetInboxListTask
        (   
            Activity    _activity,
            String      _itemGroupId,
            int         _startNum,
            int         _endNum,
            String      _startDate,
            String      _endDate
        )
        {
            mActivity    = _activity;
            mItemGroupId = _itemGroupId;
            mStartNum    = _startNum;
            mEndNum      = _endNum;
            mStartDate   = _startDate;
            mEndDate     = _endDate;
        }
        
        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            
            if( null == mActivity || null == mOnGetInboxListListener )
            {
                cancel( true );
            }
            
            mMoreInboxVOList = new ArrayList<InBoxVO>();
            
            // Progress Dialog ??? ????????????.
            // show progress dialog
            // ================================================================
            showProgressDialog( mActivity );
            // ================================================================
        }
        
        @Override
        protected void onCancelled()
        {
            dismissProgressDialog();
            super.onCancelled();
        }

        @Override
        protected Boolean doInBackground( String... params )
        {
            try
            {
                // IAP ????????? ?????? ?????? Interface ??? ???????????? ????????? ????????????.
                // call getItemsInbox() method of IAPService
                // ============================================================
                Bundle bundle = getItemsInbox( mItemGroupId,
                                               mStartNum,
                                               mEndNum,
                                               mStartDate,
                                               mEndDate );
                // ============================================================
                
                // status Code, error String ??? ???????????????
                // save status code, error string
                // ============================================================
                mErrorVO.setErrorCode( bundle.getInt( KEY_NAME_STATUS_CODE ) );
                
                mErrorVO.setErrorString( bundle.getString( 
                                                     KEY_NAME_ERROR_STRING ) );
                // ============================================================
                
                if( IAP_ERROR_NONE == mErrorVO.getErrorCode() )
                {
                    ArrayList<String> purchaseItemStringList = 
                             bundle.getStringArrayList( KEY_NAME_RESULT_LIST );
                
                    if( purchaseItemStringList != null )
                    {
                        for( String itemString : purchaseItemStringList )
                        {
                            InBoxVO inboxVO = new InBoxVO( itemString );
                            
                            // TODO  ?????? ??????
                            // ------------------------------------------------
                            Log.i( TAG, "S================================>" );
                            Log.i( TAG, inboxVO.dump() );
                            Log.i( TAG, "E================================>" );
                            // ------------------------------------------------

                            mMoreInboxVOList.add( inboxVO );
                        }
                    }
                    else
                    {
                        Log.d( TAG, "Bundle Value 'RESULT_LIST' is null." );
                    }
                }
                else
                {
                    Log.d( TAG, mErrorVO.getErrorString() );
                }
            }
            catch( Exception e )
            {
                e.printStackTrace();
                return false;
            }
            
            return true;
        }

        @Override
        protected void onPostExecute( Boolean _result )
        {
            // 1. ????????? ????????? ??????
            //    result is true
            // ================================================================
            if( true == _result )
            {
                // 1) ?????? ????????? ??????????????? ???????????? ??????
                //    If list of purchase is successfully loaded
                // ============================================================
                if( mErrorVO.getErrorCode() == IAP_ERROR_NONE )
                {
                    // Callback method call
                    // --------------------------------------------------------
                    if( null != mOnGetInboxListListener )
                    {
                        mOnGetInboxListListener.OnSucceedGetInboxList(
                                                            mMoreInboxVOList );
                    }
                    // --------------------------------------------------------
                }
                // ============================================================
                // 2) ?????? ????????? ???????????? ?????? ????????? ????????? ??????
                //    If error is occurred during loading list of purchase
                // ============================================================ 
                else
                {
                    dismissProgressDialog();
                    
                    showIapDialog(
                               mActivity,
                               "in_app_purchase ", 
                               "msg_failed_to_load_list_of_purchase" +
                                   "\n\n" + mErrorVO.getErrorString(), 
                               false,
                               null );
                }
                // ============================================================
            }
            // 2. result is false
            // ================================================================
            else
            {
                dismissProgressDialog();
                
                showIapDialog( mActivity,
                               "in_app_purchase ", 
                               "msg_failed_to_load_list_of_purchase ",
                               false,
                               null );
            }
            // ================================================================
        }
    }
    
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////// VerifyClientToServer ////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    /**
     * Execute VerifyClientToServerTask
     * @param _activity
     * @param _purchaseVO
     */
    public void verifyPurchaseResult
    (   
        Activity    _activity,
        PurchaseVO  _purchaseVO
    )
    {
        try
        {
            if( mVerifyClientToServer != null &&
                mVerifyClientToServer.getStatus() != Status.FINISHED )
            {
                mVerifyClientToServer.cancel( true );
            }

            mVerifyClientToServer = new VerifyClientToServer( _activity,
                                                              _purchaseVO );
            mVerifyClientToServer.execute();
        }
        catch( RejectedExecutionException e )
        {
            e.printStackTrace();
        }
        catch( Exception e )
        {
            e.printStackTrace();
        }
    }
    
    /**
     * IAP ?????? ????????? ????????? PurchaseVO??? verifyUrl??? purchaseId ?????????
     * ?????? ????????? ???????????? ????????????. ???????????? ????????? ?????? ?????? ?????? ????????????
     * ????????????.<BR>
     * verify purchased result
     * <BR><BR>
     * 
     * ??? ?????? ????????? ????????? ???????????? ThirdParty ???????????? ????????? ????????? ?????? ?????????.<BR>
     * ??? For a more secure transaction we recommend to verify from your server to IAP server.
     */
    private class VerifyClientToServer  extends AsyncTask<Void, Void, Boolean>
    {
        PurchaseVO       mPurchaseVO      = null;
        VerificationVO   mVerificationVO  = null;
        Activity         mActivity        = null;
        
        public VerifyClientToServer
        (   
            Activity    _activity,
            PurchaseVO  _purchaseVO
        )
        {
            mActivity   = _activity;
            mPurchaseVO = _purchaseVO;
        }
        
        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            
            if( null == mPurchaseVO ||
                true == TextUtils.isEmpty( mPurchaseVO.getVerifyUrl() ) ||
                true == TextUtils.isEmpty( mPurchaseVO.getPurchaseId() ) ||
                true == TextUtils.isEmpty( mPurchaseVO.getPaymentId() ) ||
                null == mActivity )
            {
                cancel( true );
            }
            
            showProgressDialog( mActivity );
        }
        
        @Override
        protected void onCancelled()
        {
            dismissProgressDialog();
            super.onCancelled();
        }
        
        @Override
        protected Boolean doInBackground( Void... params )
        {
            try
            {
                StringBuffer strUrl = new StringBuffer();
                strUrl.append( mPurchaseVO.getVerifyUrl() );
                strUrl.append( "&purchaseID=" + mPurchaseVO.getPurchaseId() );
                
                int     retryCount  = 0;
                String  strResponse = null;
                
                do
                {
                    strResponse = getHttpGetData( strUrl.toString(),
                                                  10000,
                                                  10000 );
                    
                    retryCount++;
                }
                while( retryCount < 3 &&
                       true == TextUtils.isEmpty( strResponse ) );
                
                
                if( strResponse == null || TextUtils.isEmpty( strResponse ) )
                {
                    return false;
                }
                else
                {
                    mVerificationVO = new VerificationVO( strResponse );
                    
                    if( mVerificationVO != null &&
                        true == "true".equals( mVerificationVO.getStatus() ) &&
                        true == mPurchaseVO.getPaymentId().equals( mVerificationVO.getPaymentId() ) )
                    {
                        return true;
                    }
                    else
                    {
                        return false;
                    }
                }
            }
            catch( Exception e )
            {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute( Boolean result )
        {
            dismissProgressDialog();
            
            if( true == result )
            {
                showIapDialog(
                     mActivity,
                     "dlg_title_payment_success ",
                     "dlg_msg_payment_success ",
                     false,
                     null );
            }
            else
            {
                showIapDialog(
                       mActivity,
                       "dlg_title_payment_error ",           
                       "msg_invalid_purchase ", 
                       false,
                       null );
            }
        }
        
        private String getHttpGetData
        (
            final String _strUrl,
            final int    _connTimeout,
            final int    _readTimeout
        )
        {
            String                  strResult       = null;
            URLConnection           con             = null;
            HttpURLConnection       httpConnection  = null;
            BufferedInputStream     bis             = null; 
            ByteArrayOutputStream   buffer          = null;
            
            try 
            {
                URL url = new URL( _strUrl );
                con = url.openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(10000);
                
                httpConnection = (HttpURLConnection)con;
                httpConnection.setRequestMethod( "GET" );
                httpConnection.connect();
                  
                int responseCode = httpConnection.getResponseCode();

                if( responseCode == 200 )
                {
                    bis = new BufferedInputStream( httpConnection.getInputStream(),
                                                   4096 );
    
                    buffer = new ByteArrayOutputStream( 4096 );             
            
                    byte [] bData = new byte[ 4096 ];
                    int nRead;
                    
                    while( ( nRead = bis.read( bData, 0, 4096 ) ) != -1 )
                    {
                        buffer.write( bData, 0, nRead );
                    }
                    
                    buffer.flush();
                    
                    strResult = buffer.toString();
                }
            } 
            catch( Exception e ) 
            {
                e.printStackTrace();
            }
            finally
            {
                if( bis != null )
                {
                    try { bis.close(); } catch (Exception e) {}
                }
                
                if( buffer != null )
                {
                    try { buffer.close(); } catch (IOException e) {}
                }
                con = null;
                httpConnection = null;
           }
            
           return strResult;
        }
    }
}