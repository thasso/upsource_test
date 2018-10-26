package com.castlabs.drmtoday;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringDef;
import android.util.Base64;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.ExoMediaDrm;
import com.google.android.exoplayer2.drm.MediaDrmCallback;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * An implementation of the the {@link MediaDrmCallback} that integrates
 * <a href="https://castlabs.com/drmtoday/https://castlabs.com/drmtoday/">DRMtoday</a>.
 *
 */
@SuppressWarnings("WeakerAccess")
public class DrmtodayCallback implements com.google.android.exoplayer2.drm.MediaDrmCallback {
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
            DRMTODAY_PRODUCTION,
            DRMTODAY_STAGING,
            DRMTODAY_TEST
    })
    public @interface DrmtodayEnvironment {}
    /**
     * Base URI for the DRMtoday production environment
     */
    public static final String DRMTODAY_PRODUCTION = "https://lic.drmtoday.com";
    /**
     * Base URI for the DRMtoday staging environment
     */
    public static final String DRMTODAY_STAGING = "https://lic.staging.drmtoday.com";
    /**
     * Base URI for the DRMtoday test environment
     */
    public static final String DRMTODAY_TEST = "https://lic.test.drmtoday.com";

    /**
     * The playready path
     */
    private static final String PLAYREADY_LICENSE_BASE_PATH = "license-proxy-headerauth/drmtoday/RightsManager.asmx";

    /**
     * The widevine path
     */
    private static final String WIDEVINE_LICENSE_BASE_PATH = "license-proxy-widevine/cenc/";

    /**
     * Number of permitted manual redirects
     */
    private static final int MAX_MANUAL_REDIRECTS = 5;
    /**
     * The assetId of the requested asset.
     */
    private static final String DRMTODAY_ASSET_ID_PARAM = "assetId";
    /**
     * (optional) The variantId of the requested asset. If no variantId is used for identifying the asset, leave out the Query parameter.
     */
    private static final String DRMTODAY_VARIANT_ID_PARAM = "variantId";
    /**
     * Debug purposes.
     */
    private static final String DRMTODAY_LOG_REQUEST_ID_PARAM = "logRequestId";
    /**
     * All DRM Today calls use a random request Id that helps checking the request on the server.
     * Print this request id on all logs when possible.
     * This parameter should be generated and not manually set.
     */
    private static final int REQUEST_ID_SIZE = 16;
    /**
     * Empty byte array
     */
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private final HttpDataSource.Factory dataSourceFactory;
    private String drmTodayUrl;
    private String assetId;
    private String variantId;
    private String merchant;
    private String userId;
    private String sessionId;
    private String authToken;

    /**
     * Create an instance of the DRMtoday  callback. Note that this instance is not configured
     * yet and you need to call {@link #configure(String, String, String, String, String, String, String)}
     * before the first DRM license request is triggered.
     *
     * @param dataSourceFactory The data source factory
     */
    public DrmtodayCallback(final HttpDataSource.Factory dataSourceFactory) {
        this.dataSourceFactory = dataSourceFactory;
    }

    /**
     * Create an instance of the DRMtoday callback with initial configuration values.
     *
     * @param dataSourceFactory The data source factory
     * @param drmTodayUrl       The DRMtoday Widevine backend URL. One of {@link #DRMTODAY_PRODUCTION},
     *                          {@link #DRMTODAY_STAGING}, or {@link #DRMTODAY_TEST}
     * @param merchant          The merchant identifer (mandatory)
     * @param assetId           The assetID for the content. This is not strictly required for Widevine but
     *                          needs to be set if multiple keys (i.e different keys for Audio/SD/HD) should be
     *                          handled by the same session.
     * @param variantId         The variantID or {@code null}
     * @param userId            The userID (mandatory)
     * @param sessionId         The sessionID (mandatory)
     */
    public DrmtodayCallback(
            final HttpDataSource.Factory dataSourceFactory,
            @DrmtodayEnvironment final String drmTodayUrl,
            @NonNull final String merchant,
            @NonNull final String userId,
            @NonNull final String sessionId,
            @Nullable final String assetId,
            @Nullable final String variantId) {
        this(dataSourceFactory, drmTodayUrl, merchant, userId, sessionId, null, assetId, variantId);
    }

    /**
     * Create an instance of the DRMtoday callback with initial configuration values.
     *
     * @param dataSourceFactory The data source factory
     * @param drmTodayUrl       The DRMtoday Widevine backend URL. One of {@link #DRMTODAY_PRODUCTION},
     *                          {@link #DRMTODAY_STAGING}, or {@link #DRMTODAY_TEST}
     * @param merchant          The merchant identifer (mandatory)
     * @param assetId           The assetID for the content. This is not strictly required for Widevine but
     *                          needs to be set if multiple keys (i.e different keys for Audio/SD/HD) should be
     *                          handled by the same session.
     * @param variantId         The variantID or {@code null}
     * @param userId            The userID (mandatory)
     * @param sessionId         The sessionID (mandatory)
     * @param authToken         The auth token or {@code null} if callback with userID/sessionID is used
     */
    @SuppressWarnings("ConstantConditions")
    public DrmtodayCallback(
            final HttpDataSource.Factory dataSourceFactory,
            @DrmtodayEnvironment final String drmTodayUrl,
            @NonNull final String merchant,
            @NonNull final String userId,
            @NonNull final String sessionId,
            @Nullable final String authToken,
            @Nullable final String assetId,
            @Nullable final String variantId) {
        this.dataSourceFactory = dataSourceFactory;
        configure(drmTodayUrl, merchant, userId, sessionId, authToken, assetId, variantId);
    }

    /**
     * Create an instance of the DRMtoday Widevine callback.
     *
     * @param drmTodayUrl       The DRMtoday Widevine backend URL. One of {@link #DRMTODAY_PRODUCTION},
     *                          {@link #DRMTODAY_STAGING}, or {@link #DRMTODAY_TEST}
     * @param merchant          The merchant identifer (mandatory)
     * @param assetId           The assetID for the content. This is not strictly required for Widevine but
     *                          needs to be set if multiple keys (i.e different keys for Audio/SD/HD) should be
     *                          handled by the same session.
     * @param variantId         The variantID or {@code null}
     * @param userId            The userID (mandatory)
     * @param sessionId         The sessionID (mandatory)
     * @param authToken         The auth token or {@code null} if callback with userID/sessionID is used
     * @throws IllegalArgumentException In case any of the mandatory parameters is not provided
     */
    @SuppressWarnings("ConstantConditions")
    public void configure(
            @DrmtodayEnvironment final String drmTodayUrl,
            @NonNull final String merchant,
            @NonNull final String userId,
            @NonNull final String sessionId,
            @Nullable final String authToken,
            @Nullable final String assetId,
            @Nullable final String variantId) {
        this.drmTodayUrl = drmTodayUrl;
        this.merchant = merchant;
        this.assetId = assetId;
        this.variantId = variantId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.authToken = authToken;
        validateConfiguration();
    }

    /**
     * Validates the configuration values.
     *
     * @throws IllegalArgumentException In case any of the mandatory parameters is not provided
     */
    private void validateConfiguration() {
        if (this.drmTodayUrl == null || this.drmTodayUrl.isEmpty()) {
            throw new IllegalArgumentException("No valid DRMtoday backend URL specified!");
        }
        if (this.merchant == null || this.merchant.isEmpty()) {
            throw new IllegalArgumentException("No valid merchant specified!");
        }
        if (this.userId == null || this.userId.isEmpty()) {
            throw new IllegalArgumentException("No valid userId specified!");
        }
        if (this.sessionId == null || this.sessionId.isEmpty()) {
            throw new IllegalArgumentException("No valid sessionId specified!");
        }
    }

    @Override
    public byte[] executeProvisionRequest(UUID uuid, ExoMediaDrm.ProvisionRequest request) throws Exception {
        String url =
                request.getDefaultUrl() + "&signedRequest=" + Util.fromUtf8Bytes(request.getData());
        return executePost(dataSourceFactory, url, EMPTY_BYTE_ARRAY, null);
    }

    @Override
    public byte[] executeKeyRequest(UUID uuid, ExoMediaDrm.KeyRequest request) throws IOException {
        try{
            validateConfiguration();
        }catch (IllegalArgumentException e) {
            throw new IOException("DRMtoday configuration invalid:" + e.getMessage(), e);
        }

        boolean widevine = uuid.equals(C.WIDEVINE_UUID);

        Map<String, String> requestProperties = null;
        // We are ignoring the default URL that might be in the key requests and always go
        // to DRMtoday
        Uri.Builder builder = Uri.parse(drmTodayUrl).buildUpon();
        builder.appendEncodedPath(widevine ? WIDEVINE_LICENSE_BASE_PATH : PLAYREADY_LICENSE_BASE_PATH);

        // Common parameters
        // We append a unique request ID
        builder.appendQueryParameter(DRMTODAY_LOG_REQUEST_ID_PARAM, generateRequestId());
        // If the asset ID and variant ID are configured, we append them
        // as query parameters to make sure that we can get all licenses (i.e. if you
        // have different keys for audio/SD/HD) for the given asset
        if (assetId != null) {
            builder.appendQueryParameter(DRMTODAY_ASSET_ID_PARAM, assetId);
        }
        if (variantId != null) {
            builder.appendQueryParameter(DRMTODAY_VARIANT_ID_PARAM, variantId);
        }

        // create a map for additional header parameters
        requestProperties = new HashMap<>();
        // Add the drmtoday custom data
        requestProperties.put("dt-custom-data",
                Base64.encodeToString(getCustomDataJSON().getBytes(), Base64.NO_WRAP));

        // if an auth token is configured, add it to the request headers
        if (authToken != null) {
            requestProperties.put("x-dt-auth-token", authToken);
        }

        if (widevine) {
            // For Widevine requests, we have to make sure the content type is set accordingly
            requestProperties.put("Content-Type", "application/octet-stream");
        } else {
            requestProperties.put("Content-Type", "text/xml");
            requestProperties.put("SOAPAction",
                    "http://schemas.microsoft.com/DRM/2007/03/protocols/AcquireLicense");
        }

        // build the URI
        Uri uri = builder.build();

        // Execute the request and catch the most common errors
        final byte[] bytes;
        try {
            bytes = executePost(dataSourceFactory, uri.toString(), request.getData(), requestProperties);
        } catch (FileNotFoundException e) {
            throw new IOException("License not found");
        } catch (IOException e) {
            throw new IOException("Error during license acquisition", e);
        }

        if (widevine) {
            try {
                JSONObject jsonObject = new JSONObject(new String(bytes));
                return Base64.decode(jsonObject.getString("license"), Base64.DEFAULT);
            } catch (JSONException e) {
                throw new IOException("Error while parsing widevine response", e);
            }
        } else {
            return bytes;
        }
    }

    /**
     * @return json object that encodes the DRMtoday opt-data
     */
    private String getCustomDataJSON() {
        JSONObject json = new JSONObject();
        try {
            json.put("userId", userId);
            json.put("sessionId", sessionId);
            json.put("merchant", merchant);
            return json.toString();
        } catch (JSONException e) {
            throw new RuntimeException("Unable to encode request data: " + e.getMessage(), e);
        }
    }

    /**
     * Create a random request ID
     */
    private static String generateRequestId() {
        byte[] byteArray = new byte[REQUEST_ID_SIZE];
        new Random().nextBytes(byteArray);
        StringBuilder sb = new StringBuilder(byteArray.length * 2);
        for (byte b : byteArray) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static byte[] executePost(HttpDataSource.Factory dataSourceFactory, String url,
                                      byte[] data, Map<String, String> requestProperties) throws IOException {
        HttpDataSource dataSource = dataSourceFactory.createDataSource();
        if (requestProperties != null) {
            for (Map.Entry<String, String> requestProperty : requestProperties.entrySet()) {
                dataSource.setRequestProperty(requestProperty.getKey(), requestProperty.getValue());
            }
        }

        int manualRedirectCount = 0;
        while (true) {
            DataSpec dataSpec =
                    new DataSpec(
                            Uri.parse(url),
                            data,
                            /* absoluteStreamPosition= */ 0,
                            /* position= */ 0,
                            /* length= */ C.LENGTH_UNSET,
                            /* key= */ null,
                            DataSpec.FLAG_ALLOW_GZIP);
            DataSourceInputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);
            try {
                return Util.toByteArray(inputStream);
            } catch (HttpDataSource.InvalidResponseCodeException e) {
                // For POST requests, the underlying network stack will not normally follow 307 or 308
                // redirects automatically. Do so manually here.
                boolean manuallyRedirect =
                        (e.responseCode == 307 || e.responseCode == 308)
                                && manualRedirectCount++ < MAX_MANUAL_REDIRECTS;
                url = manuallyRedirect ? getRedirectUrl(e) : null;
                if (url == null) {
                    throw e;
                }
            } finally {
                Util.closeQuietly(inputStream);
            }
        }
    }

    private static String getRedirectUrl(HttpDataSource.InvalidResponseCodeException exception) {
        Map<String, List<String>> headerFields = exception.headerFields;
        if (headerFields != null) {
            List<String> locationHeaders = headerFields.get("Location");
            if (locationHeaders != null && !locationHeaders.isEmpty()) {
                return locationHeaders.get(0);
            }
        }
        return null;
    }


}
