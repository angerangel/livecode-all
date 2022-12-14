package com.sec.android.iap.sample.activity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.sec.android.iap.sample.R;
import com.sec.android.iap.sample.adapter.ItemInboxListAdapter;
import com.sec.android.iap.sample.helper.SamsungIapHelper;
import com.sec.android.iap.sample.helper.SamsungIapHelper.OnGetInboxListListener;
import com.sec.android.iap.sample.helper.SamsungIapHelper.OnInitIapListener;
import com.sec.android.iap.sample.vo.InBoxVO;

public class ItemsInboxList extends Activity
    implements OnInitIapListener, OnGetInboxListListener
{
    private static final String  TAG = ItemsInboxList.class.getSimpleName();

    private ListView              mItemInboxListView    = null;
    private TextView              mNoDataTextView       = null;

    // Item Group ID of 3rd Party Application
    // ========================================================================
    private String                mItemGroupId          = null;
    // ========================================================================
    
    // Communication Helper between IAPService and 3rd Party Application
    // ========================================================================
    private SamsungIapHelper      mSamsungIapHelper     = null;
    // ========================================================================
    
    // For loading list of purchased item
    // ========================================================================
    /** ArrayList for list of purchased item */
    private ArrayList<InBoxVO>    mInboxVOList = new ArrayList<InBoxVO>();
    
    /** AdapterView for list of purchased item */
    private ItemInboxListAdapter  mItemInboxListAdapter = null;
    // ========================================================================
    
    private int mIapMode = SamsungIapHelper.IAP_MODE_COMMERCIAL;
    
    @Override
    protected void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );
        
        setContentView( R.layout.item_inbox_list_layout );

        // 1. intent ??? ????????? ????????? ?????? ???????????? ????????? ????????????.
        //    store Item Group Id and IapMode passed by Intent
        // ====================================================================
        Intent intent = getIntent();
        
        if( intent != null && intent.getExtras() != null 
                           && intent.getExtras().containsKey( "ItemGroupId" ) 
                           && intent.getExtras().containsKey( "IapMode" ) )
        {
            Bundle extras = intent.getExtras();

            mItemGroupId = extras.getString( "ItemGroupId" );
            mIapMode     = extras.getInt( "IapMode" );
        }
        else
        {
            Toast.makeText( this, 
                            R.string.invalid_activity_params,
                            Toast.LENGTH_LONG ).show();
            finish();
        }
        // ====================================================================
        
        // 2. SamsungIapHelper Instance ??????
        //    create SamsungIapHelper Instance
        //
        //    ????????? ?????? ?????? ????????? ????????? ???????????? ?????????, 
        //    SamsungIapHelper.IAP_MODE_TEST_SUCCESS ???????????????.
        //    Billing does not want to set the test mode, 
        //                          SamsungIapHelper.IAP_MODE_TEST_SUCCESS use.
        // ====================================================================
        mSamsungIapHelper = SamsungIapHelper.getInstance( this, mIapMode );
        
        // ???????????? ?????? ??????
        // For test...
        /*mSamsungIapHelper = new SamsungIapHelper( this ,
                                    SamsungIapHelper.IAP_MODE_TEST_SUCCESS );*/
        // ====================================================================
        
        // 3. OnInitIapListener ??????
        //    Register OnInitIapListener
        // ====================================================================
        mSamsungIapHelper.setOnInitIapListener( this );
        // ====================================================================
        
        // 4.OnGetInboxListListener ??????
        //   Register OnGetInboxListListener
        // ====================================================================
        mSamsungIapHelper.setOnGetInboxListListener( this );
        // ====================================================================
        
        // 5. IAP ???????????? ???????????? ?????????.
        //    If IAP Package is installed in your device
        // ====================================================================
        if( true == mSamsungIapHelper.isInstalledIapPackage( this ) )
        {
            // 1) ????????? ???????????? ????????? ???????????????
            //    If IAP package installed in your device is valid
            // ================================================================
            if( true == mSamsungIapHelper.isValidIapPackage( this ) )
            {
                // show Progress Dialog
                // ------------------------------------------------------------
                mSamsungIapHelper.showProgressDialog( this );
                // ------------------------------------------------------------
                
                // ?????? ???????????? ?????? ?????? ??????
                // process SamsungAccount authentication
                // ------------------------------------------------------------
                mSamsungIapHelper.startAccountActivity( this );
                // ------------------------------------------------------------
            }
            // ================================================================
            // 2) ????????? ???????????? ???????????? ?????????
            //    If IAP package installed in your device is not valid
            // ================================================================            
            else
            {
                // show alert dialog for invalid IAP Package
                // ------------------------------------------------------------
                mSamsungIapHelper.showIapDialog(
                                     this,
                                     getString( R.string.in_app_purchase ),           
                                     getString( R.string.invalid_iap_package ),
                                     true,
                                     null );
                // ------------------------------------------------------------
            }
            // ================================================================
        }
        // ====================================================================
        // 6. If IAP Package is not installed in your device
        // ====================================================================
        else
        {
            mSamsungIapHelper.installIapPackage( this );
        }
        // ====================================================================
        
        // 7. ??? ??????
        //    set views
        // ====================================================================
        initView();
        // ====================================================================
    }
    
    
    /**
     * IAP Service ??? ??????????????? ??????????????? ????????? ????????????
     * initIAP() ???????????? ???????????? IAP??? ????????? ??????.
     * 
     * bind IAPService. If IAPService properly bound,
     * initIAP() method is called to initialize IAPService.
     */
    public void getItemInboxListService()
    {
        // 1. ???????????? ?????????????????? Bind ????????? ??????.
        //    bind to IAPService
        // ====================================================================
        mSamsungIapHelper.bindIapService( 
                              new SamsungIapHelper.OnIapBindListener()
        {
            @Override
            public void onBindIapFinished( int result )
            {
                // 1) ????????? ???????????? ??????????????? ????????? ??????
                //    If successfully bound IAPService
                // ============================================================
                if ( result == SamsungIapHelper.IAP_RESPONSE_RESULT_OK )
                {
                    // initialize IAPService.
                    // safeGetItemInboxList method is called after IAPService
                    // is initialized
                    // --------------------------------------------------------
                    mSamsungIapHelper.safeInitIap( ItemsInboxList.this );
                    // --------------------------------------------------------
                }
                // ============================================================
                // 2) ????????? ???????????? ??????????????? ????????? ????????? ??????
                //    If IAPService is not bound correctly
                // ============================================================
                else
                {
                    // dismiss ProgressDialog
                    // --------------------------------------------------------
                    mSamsungIapHelper.dismissProgressDialog();
                    // --------------------------------------------------------
                    
                    // show alert dialog for bind failure
                    // --------------------------------------------------------
                    mSamsungIapHelper.showIapDialog(
                             ItemsInboxList.this,
                             getString( R.string.in_app_purchase ), 
                             getString( R.string.msg_iap_service_bind_failed ),
                             false,
                             null );
                    // --------------------------------------------------------
                }
                // ============================================================
            }
        });
        // ====================================================================
    }
    
    
    /**
     * initialize views
     */
    public void initView()
    {
        mItemInboxListView = (ListView)findViewById( R.id.itemInboxList );
        mNoDataTextView    = (TextView)findViewById( R.id.noDataText );
        mNoDataTextView.setVisibility( View.GONE );
        
        mItemInboxListView.setEmptyView( mNoDataTextView );
        
        mItemInboxListAdapter = new ItemInboxListAdapter( this, 
                                                       R.layout.item_inbox_row, 
                                                       mInboxVOList );
        
        mItemInboxListView.setAdapter( mItemInboxListAdapter );
    }
  

    /**
     * Samsung Account ?????? ????????? IAP ?????? ????????? ??????.
     * treat result of SamsungAccount Authentication and IAPService 
     */
    @Override
    protected void onActivityResult
    (   
        int     _requestCode,
        int     _resultCode,
        Intent  _intent
    )
    {
        switch( _requestCode )
        {
            // ?????? ???????????? ?????? ?????? ?????? ??????
            // treat result of SamsungAccount authentication
            // ================================================================
            case SamsungIapHelper.REQUEST_CODE_IS_ACCOUNT_CERTIFICATION :
            {
                // 1) ?????? ?????? ???????????? ????????? ??????
                //    If SamsungAccount authentication is succeed 
                // ------------------------------------------------------------
                if( RESULT_OK == _resultCode )
                {
                    // IAP ???????????? ?????? ?????? ????????? ????????? ?????????.
                    // Get purchased item list via IAPService 
                    // --------------------------------------------------------
                    getItemInboxListService();
                    // --------------------------------------------------------
                }
                // ------------------------------------------------------------
                // 2) ?????? ?????? ????????? ???????????? ??????
                //    If SamsungAccount authentication is cancelled
                // ------------------------------------------------------------
                else if( RESULT_CANCELED == _resultCode )
                {
                    // ?????????????????? dismiss ???????????????.
                    // dismiss ProgressDialog for SamsungAccount Authentication
                    // --------------------------------------------------------
                    mSamsungIapHelper.dismissProgressDialog();
                    // --------------------------------------------------------
                    
                    mSamsungIapHelper.showIapDialog(
                           ItemsInboxList.this,
                           getString( R.string.dlg_title_samsungaccount_authentication ),
                           getString( R.string.msg_authentication_has_been_cancelled ),
                           false,
                           null );
                }
                // ------------------------------------------------------------
                break;
            }
            // ================================================================
        }
    }
    
    @Override
    public void onSucceedInitIap()
    {
        Date d = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat( "yyyyMMdd",
                                                     Locale.getDefault() );
        String today = sdf.format( d );
        
        mSamsungIapHelper.safeGetItemInboxTask( this, 
                                                mItemGroupId,
                                                1,
                                                15,
                                                "20130101",
                                                today );
    }
    
    @Override
    public void OnSucceedGetInboxList( ArrayList<InBoxVO>  _inboxList )
    {
        Log.i( TAG, "getInboxList has finished successfully" );
        
        mSamsungIapHelper.dismissProgressDialog();
        
        mInboxVOList.addAll( _inboxList );
        
        if( mInboxVOList.size() > 0 )
        {
            mItemInboxListView.setVisibility( View.VISIBLE );
            mNoDataTextView.setVisibility( View.GONE );
        }
        
        mItemInboxListAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        if( null != mSamsungIapHelper )
        {
            mSamsungIapHelper.stopRunningTask();
        }
    }
}