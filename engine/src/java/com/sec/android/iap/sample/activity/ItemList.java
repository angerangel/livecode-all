package com.sec.android.iap.sample.activity;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.sec.android.iap.sample.R;
import com.sec.android.iap.sample.adapter.ItemListAdapter;
import com.sec.android.iap.sample.helper.SamsungIapHelper;
import com.sec.android.iap.sample.helper.SamsungIapHelper.OnGetItemListListener;
import com.sec.android.iap.sample.helper.SamsungIapHelper.OnInitIapListener;
import com.sec.android.iap.sample.vo.ItemVO;
import com.sec.android.iap.sample.vo.PurchaseVO;

public class ItemList extends Activity
    implements OnInitIapListener, OnGetItemListListener
{
    private static final String  TAG = ItemList.class.getSimpleName();

    private ListView          mItemListView      = null;
    
    private TextView          mNoDataTextView    = null,
                              mSelectedItemType  = null;
    
    // Item Group ID of 3rd Party Application
    // ========================================================================
    private String            mItemGroupId       = null;
    // ========================================================================
    
    /** Item Type
     *  Consumable      : 00
     *  Non Consumable  : 01
     *  Subscription    : 02
     *  All             : 10
     */
    private String            mItemType          = null;
    
    // Communication Helper between IAPService and 3rd Party Application
    // ========================================================================
    private SamsungIapHelper  mSamsungIapHelper  = null;
    // ========================================================================
    
    // For Loading Item List
    // ========================================================================
    /** ArrayList for Item */
    private ArrayList<ItemVO> mItemVOList        = new ArrayList<ItemVO>();
    
    /** AdapterView for ItemList */
    private ItemListAdapter   mItemListAdapter   = null;
    // ========================================================================

    // ????????? ??????????????? ????????? ?????????
    // selected item in mItemListView
    // ========================================================================
    private ItemVO mSelectedItemVO = null;
    // ========================================================================
    
    private int mIapMode = SamsungIapHelper.IAP_MODE_COMMERCIAL;
    
    @Override
    protected void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );
        
        setContentView( R.layout.item_list_layout );

        // 1. intent ??? ????????? ????????? ?????? ???????????? ????????? ????????? ????????????. 
        //    store Item Group Id, Item Type and IapMode passed by Intent
        // ====================================================================
        Intent intent = getIntent();
        
        if( intent != null && intent.getExtras() != null 
                           && intent.getExtras().containsKey( "ItemGroupId" ) 
                           && intent.getExtras().containsKey( "ItemType" )
                           && intent.getExtras().containsKey( "IapMode" ) )
        {
            Bundle extras = intent.getExtras();

            mItemGroupId = extras.getString( "ItemGroupId" );
            mItemType    = extras.getString( "ItemType" );
            mIapMode     = extras.getInt( "IapMode" );
        }
        else
        {
            Toast.makeText( this, 
                            "invalid_activity_params",
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
        //    SamsungIapHelper.IAP_MODE_TEST_SUCCESS use.
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
        
        // 4. OnGetItemListListener ??????
        //    Register OnGetItemListListener
        // ====================================================================
        mSamsungIapHelper.setOnGetItemListListener( this );
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
                // ???????????? ????????? ?????? Item List ??? ????????????.
                // bind to IAPService and then get item list
                // ------------------------------------------------------------
                getItemListService();
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
                                     getString( "in_app_purchase" ),           
                                     getString( "invalid_iap_package"),
                                     true,
                                     null );
                // ------------------------------------------------------------
            }
            // ================================================================ 
        }
        // 6. IAP ???????????? ???????????? ?????? ?????????
        //    If IAP Package is not installed in your device
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
     * safeGetItemListTask() ???????????? ???????????? ??????????????? ????????? ????????? ????????????.
     * 
     * bind IAPService. If IAPService properly bound,
     * safeGetItemListTask() method is called to get item list.
     */
    public void getItemListService()
    {
        // 1. ???????????? ?????????????????? Bind ????????? ??????.
        //    bind IAPService
        // ====================================================================
        mSamsungIapHelper.bindIapService( 
                                       new SamsungIapHelper.OnIapBindListener()
        {
            @Override
            public void onBindIapFinished( int result )
            {
                // 1) ????????? ???????????? ??????????????? ????????? ?????? ????????? ????????? ????????????.
                //    If successfully bound IAPService
                // ============================================================
                if( result == SamsungIapHelper.IAP_RESPONSE_RESULT_OK )
                {
                    // Get item list
                    // --------------------------------------------------------
                    mSamsungIapHelper.safeGetItemList( ItemList.this,
                                                       mItemGroupId,
                                                       1, 15, // Items from 1st to 15th
                                                       mItemType );
                    // --------------------------------------------------------
                }
                // ============================================================
                // 2) ????????? ???????????? ??????????????? ????????? ????????? ?????? ??????????????? ??????
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
                             ItemList.this,
                             getString( "in_app_purchase" ), 
                             getString( "msg_iap_service_bind_failed" ),
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
        // 1. ???????????? ?????? ??? ??????
        //    set views of Item List
        // ====================================================================
        mItemListView   = (ListView)findViewById( R.id.itemList );
        mNoDataTextView = (TextView)findViewById( R.id.noDataText );
        
        mSelectedItemType = (TextView)findViewById(
                                                 R.id.txt_selected_item_type );
        mSelectedItemType.setText( mItemType );
        
        mItemListAdapter = new ItemListAdapter( this, 
                                                R.layout.item_row, 
                                                mItemVOList );
        
        mItemListView.setAdapter( mItemListAdapter );
        mItemListView.setEmptyView( mNoDataTextView );
        mItemListView.setVisibility( View.GONE );
        // ====================================================================
        
        // 2. ???????????? ?????????????????? ????????? ???????????? IAP??? ??????????????????
        //    (PaymentMethodListActivity)?????? ???????????? ?????????
        //    ????????? ?????? ????????? ????????????.
        //    1) ?????? ???????????? ?????? ?????? ??????
        //    2) IAPService ?????????
        //    3) PaymentMethodListActivity ??????
        // 
        //    If you click a Item in Item List, process below to go to
        //    PaymentMethodListActivity of IAP
        //    1) SamsungAccount authentication process
        //    3) IAPService initialization
        //    3) call PaymentMethodListActivity
        // ====================================================================
        mItemListView.setOnItemClickListener(
                                          new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick
            (   
                AdapterView<?>  _parent,
                View            _view,
                int             _position,
                long            _id
            )
            {
                mSelectedItemVO = mItemVOList.get( _position );

                if( mSelectedItemVO != null )
                {
                    // show ProgressDialog
                    // --------------------------------------------------------
                    mSamsungIapHelper.showProgressDialog( ItemList.this );
                    // --------------------------------------------------------
                    
                    // ?????? ???????????? ?????? ?????? ??????
                    // process SamsungAccount authentication
                    // --------------------------------------------------------
                    mSamsungIapHelper.startAccountActivity( ItemList.this );
                    // --------------------------------------------------------
                }
                else
                {
                    Log.d( TAG, "Selected item is null." );
                }
            }
        } );
        // ====================================================================
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
        switch ( _requestCode )
        {
            // 1. IAP ?????? ?????? ??????
            //    treat result of IAPService
            // ================================================================
            case SamsungIapHelper.REQUEST_CODE_IS_IAP_PAYMENT:
            {
                if( null == _intent )
                {
                    break;
                }
                
                Bundle extras         = _intent.getExtras();
                
                String itemId         = "";
                String thirdPartyName = "";

                // payment success   : 0
                // payment cancelled : 1
                // ============================================================
                int statusCode        = 1;
                // ============================================================
                
                String errorString    = "";
                PurchaseVO purchaseVO = null;
                
                // 1) IAP ?????? ????????? Bundle ????????? ????????? ??????
                //    If there is bundle passed from IAP
                // ------------------------------------------------------------
                if( null != extras )
                {
                    thirdPartyName = extras.getString(
                                  SamsungIapHelper.KEY_NAME_THIRD_PARTY_NAME );
                    
                    statusCode = extras.getInt( 
                                       SamsungIapHelper.KEY_NAME_STATUS_CODE );
                    
                    errorString = extras.getString( 
                                      SamsungIapHelper.KEY_NAME_ERROR_STRING );
                    
                    itemId = extras.getString(
                                           SamsungIapHelper.KEY_NAME_ITEM_ID );
                    
                    // ?????? ?????? : ????????? ?????? ???????????????.
                    // print log : Please remove before release
                    // --------------------------------------------------------
                    Log.i( TAG, "3rdParty Name : " + thirdPartyName + "\n" +
                                "ItemId        : " + itemId + "\n" +
                                "StatusCode    : " + statusCode + "\n" +
                                "errorString   : " + errorString );
                    // --------------------------------------------------------
                }
                // ------------------------------------------------------------
                // 2) IAP ?????? ????????? Bundle ????????? ???????????? ?????? ??????
                //    If there is no bundle passed from IAP
                // ------------------------------------------------------------
                else
                {
                    mSamsungIapHelper.showIapDialog(
                        ItemList.this,
                        getString( "dlg_title_payment_error" ), 
                        getString( "msg_payment_was_not_processed_successfully"),
                        false,
                        null );
                }
                // ------------------------------------------------------------
                // 3) ????????? ???????????? ?????? ??????
                //    If payment was not cancelled
                // ------------------------------------------------------------
                if( RESULT_OK == _resultCode )
                {
                    // a. IAP ?????? ????????? ?????? ????????? ????????? ?????? verifyurl ??? 
                    //    purchaseId ????????? ????????? ?????? ????????? ????????? ??? ????????????.
                    //    if Payment succeed
                    // --------------------------------------------------------
                    if( statusCode == SamsungIapHelper.IAP_ERROR_NONE )
                    {
                        // ??????????????? ????????? ??????????????? PurchaseVO??? ????????????.
                        // make PurcahseVO
                        // ----------------------------------------------------
                        purchaseVO = new PurchaseVO( extras.getString(
                                   SamsungIapHelper.KEY_NAME_RESULT_OBJECT ) );
                        // ----------------------------------------------------
                        
                        // ?????? ???????????? ????????????.
                        // verify payment result
                        // ----------------------------------------------------
                        mSamsungIapHelper.verifyPurchaseResult( ItemList.this,
                                                                purchaseVO );
                        // ----------------------------------------------------
                    }
                    // --------------------------------------------------------
                    // b. IAP ?????? ????????? ?????? ????????? ????????? ?????? ?????????????????? ??????
                    //    Payment failed 
                    // --------------------------------------------------------
                    else
                    {
                        mSamsungIapHelper.showIapDialog(
                                 ItemList.this,
                                 getString( R.string.dlg_title_payment_error ),           
                                 errorString,
                                 false,
                                 null);

                    }
                    // --------------------------------------------------------
                }
                // ------------------------------------------------------------
                // 4) ????????? ????????? ??????
                //    If payment was cancelled
                // ------------------------------------------------------------
                else if( RESULT_CANCELED == _resultCode )
                {
                    mSamsungIapHelper.showIapDialog(
                             ItemList.this,
                             getString( R.string.dlg_title_payment_cancelled ),
                             getString( R.string.dlg_msg_payment_cancelled ),
                             false,
                             null );
                }
                // ------------------------------------------------------------
                
                break;
            }
            // ================================================================
            
            // 2. ?????? ???????????? ?????? ?????? ?????? ??????
            //    treat result of SamsungAccount authentication
            // ================================================================
            case SamsungIapHelper.REQUEST_CODE_IS_ACCOUNT_CERTIFICATION :
            {
                // 1) ?????? ?????? ???????????? ????????? ??????
                //    If SamsungAccount authentication is succeed 
                // ------------------------------------------------------------
                if( RESULT_OK == _resultCode )
                {
                    // IAP ???????????? ????????? ??????.
                    // initialize IAPService
                    // --------------------------------------------------------
                    mSamsungIapHelper.safeInitIap( ItemList.this );
                    // --------------------------------------------------------
                }
                // ------------------------------------------------------------
                // 2) ?????? ?????? ????????? ???????????? ??????
                //    If SamsungAccount authentication is cancelled
                // ------------------------------------------------------------
                else if( RESULT_CANCELED == _resultCode )
                {
                    // ?????????????????? dismiss ???????????????.
                    // dismiss ProgressDialog
                    // --------------------------------------------------------
                    mSamsungIapHelper.dismissProgressDialog();
                    // --------------------------------------------------------
                    
                    mSamsungIapHelper.showIapDialog(
                             ItemList.this,
                             getString( R.string.dlg_title_samsungaccount_authentication ),
                             getString( R.string.msg_authentication_has_been_cancelled ),
                             false,
                             null);
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
        // ??????????????? ?????????????????? dismiss ??????
        // dismiss ProgressDialog
        // ====================================================================
        mSamsungIapHelper.dismissProgressDialog();
        // ====================================================================
        
        if( mSelectedItemVO != null )
        {
            // ???????????? ??????????????? ??????????????? ????????? IAP ???????????? ????????? ??????
            // call PurchaseMethodListActivity of IAP
            // ----------------------------------------------------------------
            mSamsungIapHelper.startPurchase( 
                                  ItemList.this, 
                                  SamsungIapHelper.REQUEST_CODE_IS_IAP_PAYMENT, 
                                  mItemGroupId,
                                  mSelectedItemVO.getItemId() );
            // ----------------------------------------------------------------
        }
        else
        {
            Log.e(TAG, "There is no selected item" );
        }
    }
    
    @Override
    public void onSucceedGetItemList
    (   
        ArrayList<ItemVO>   _itemList
    )
    {
        mSamsungIapHelper.dismissProgressDialog();

        mItemVOList.addAll( _itemList );
        
        if( mItemVOList.size() > 0 )
        {
            mItemListView.setVisibility( View.VISIBLE );
            mNoDataTextView.setVisibility( View.GONE );
        }
        
        mItemListAdapter.notifyDataSetChanged();
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