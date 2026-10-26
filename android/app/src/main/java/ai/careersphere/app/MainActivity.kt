package ai.careersphere.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * CareerSphere on Android: the CareerSphere web app in a full-screen WebView.
 *
 * Start the servers on your Mac first (./run.sh, or the VS Code task). In the Android emulator
 * the Mac is 10.0.2.2, so the app opens http://10.0.2.2:5173. On a real phone, or for the
 * deployed site, type its address on the start screen when the app asks.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var startup: View
    private lateinit var spinner: ProgressBar
    private lateinit var status: TextView
    private lateinit var addressBox: View
    private lateinit var address: EditText

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var attempt = 0
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    /** <input type="file"> (resume upload) opens the phone's file picker. */
    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }

    private val server: String
        get() = getSharedPreferences("careersphere", MODE_PRIVATE)
            .getString("server", BuildConfig.DEFAULT_SERVER) ?: BuildConfig.DEFAULT_SERVER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.web)
        refresh = findViewById(R.id.refresh)
        startup = findViewById(R.id.startup)
        spinner = findViewById(R.id.spinner)
        status = findViewById(R.id.status)
        addressBox = findViewById(R.id.address_box)
        address = findViewById(R.id.address)

        // Keep content clear of the status bar, navigation bar and keyboard (Android 15 draws edge to edge).
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setUpWebView()
        refresh.setOnRefreshListener { web.reload() }

        findViewById<Button>(R.id.connect).setOnClickListener { saveAddressAndConnect() }
        address.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_GO) { saveAddressAndConnect(); true } else false
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (refresh.visibility == View.VISIBLE && web.canGoBack()) web.goBack() else finish()
            }
        })

        if (savedInstanceState != null && web.restoreState(savedInstanceState) != null) {
            showWeb()
        } else {
            connect()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true          // the login token is kept in localStorage
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
        }
        web.addJavascriptInterface(FileBridge(), "CareerSphereAndroid")

        web.webViewClient = object : WebViewClient() {
            /** Links to other sites (GitHub, LinkedIn) open in the browser. */
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url
                val home = Uri.parse(server)
                if (target.host == home.host && target.port == home.port) return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, target)); true
                } catch (e: ActivityNotFoundException) {
                    false
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                refresh.isRefreshing = false
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showFailure("Lost the connection to CareerSphere.")
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                // The introduction video asks for a video: offer the camera app next to the file picker.
                val wantsVideo = params.acceptTypes.any { it.startsWith("video") }
                val intent = if (wantsVideo) {
                    val record = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                        .putExtra(MediaStore.EXTRA_DURATION_LIMIT, 60)
                        .putExtra("android.intent.extras.CAMERA_FACING", 1)
                    Intent.createChooser(params.createIntent(), "Introduction video")
                        .putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(record))
                } else params.createIntent()
                return try {
                    pickFile.launch(intent); true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null; false
                }
            }
        }
    }

    // ------------------------------------------------------------ connecting

    /** Waits until the server answers /api/health (through the web app), then shows it. */
    private fun connect() {
        val myAttempt = ++attempt
        val base = server
        startup.visibility = View.VISIBLE
        refresh.visibility = View.GONE
        addressBox.visibility = View.GONE
        spinner.visibility = View.VISIBLE
        status.text = getString(R.string.connecting) + "\n" + base

        io.execute {
            val deadline = System.currentTimeMillis() + 30_000
            var up = false
            while (!up && System.currentTimeMillis() < deadline && myAttempt == attempt) {
                up = isUp("$base/api/health")
                if (!up) try { Thread.sleep(2_000) } catch (e: InterruptedException) { return@execute }
            }
            main.post {
                if (myAttempt != attempt) return@post
                if (up) {
                    web.loadUrl(base)
                    showWeb()
                } else {
                    showFailure(
                        "CareerSphere isn't answering at\n$base\n\n" +
                            "Start the servers on your Mac (./run.sh), then tap Connect. " +
                            "On a real phone, use your Mac's Wi-Fi address, for example http://192.168.1.5:5173."
                    )
                }
            }
        }
    }

    private fun isUp(url: String): Boolean = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 2_000
        c.readTimeout = 2_000
        val ok = c.responseCode == 200
        c.disconnect()
        ok
    } catch (e: Exception) {
        false
    }

    private fun showWeb() {
        startup.visibility = View.GONE
        refresh.visibility = View.VISIBLE
    }

    private fun showFailure(message: String) {
        startup.visibility = View.VISIBLE
        refresh.visibility = View.GONE
        spinner.visibility = View.GONE
        status.text = message
        address.setText(server)
        addressBox.visibility = View.VISIBLE
    }

    private fun saveAddressAndConnect() {
        var a = address.text.toString().trim().trimEnd('/')
        if (a.isEmpty()) a = BuildConfig.DEFAULT_SERVER
        if (!a.startsWith("http://") && !a.startsWith("https://")) a = "http://$a"
        getSharedPreferences("careersphere", MODE_PRIVATE).edit().putString("server", a).apply()
        connect()
    }

    // ------------------------------------------------------------ opening resumes

    /**
     * Called by the web app (window.CareerSphereAndroid.openFile) when a recruiter opens a resume.
     * WebView can't show PDFs, so the file is saved to the app's cache and opened in the phone's viewer.
     */
    inner class FileBridge {
        @JavascriptInterface
        fun openFile(base64: String, mimeType: String, fileName: String) {
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "resume.pdf" }
            val dir = File(cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, safeName)
            file.writeBytes(Base64.decode(base64, Base64.DEFAULT))
            val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
            main.post {
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, mimeType)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this@MainActivity, R.string.no_viewer, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
