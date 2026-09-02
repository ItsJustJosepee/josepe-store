package store.josepe.dev

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.*
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import com.sun.jna.Structure
import java.awt.Window

object WindowsWindowProcSubclass {

    private const val GWLP_WNDPROC = -4
    private const val WM_GETMINMAXINFO = 0x0024
    private const val WM_NCCALCSIZE = 0x0083
    private const val WM_NCHITTEST = 0x0084
    private const val WM_CONTEXTMENU = 0x007B
    private const val WM_NCLBUTTONDOWN = 0x00A1

    private const val HTCLIENT = 1
    private const val HTCAPTION = 2
    private const val HTLEFT = 10
    private const val HTRIGHT = 11
    private const val HTTOP = 12
    private const val HTTOPLEFT = 13
    private const val HTTOPRIGHT = 14
    private const val HTBOTTOM = 15
    private const val HTBOTTOMLEFT = 16
    private const val HTBOTTOMRIGHT = 17

    interface DirectUser32 : StdCallLibrary {
        fun SetWindowLongPtr(hWnd: HWND, nIndex: Int, wndProc: WinUser.WindowProc): Pointer
        fun SetWindowLong(hWnd: HWND, nIndex: Int, wndProc: WinUser.WindowProc): Pointer
        fun SetWindowLongPtr(hWnd: HWND, nIndex: Int, dwNewLong: Pointer): Pointer
        fun SetWindowLong(hWnd: HWND, nIndex: Int, dwNewLong: Pointer): Pointer
        fun CallWindowProc(prevWndProc: Pointer, hWnd: HWND, uMsg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
        fun ReleaseCapture(): Boolean
        fun SendMessageW(hWnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT

        companion object {
            val INSTANCE: DirectUser32 = Native.load("user32", DirectUser32::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }

    private val subclassedWindows = java.util.concurrent.ConcurrentHashMap<HWND, Pointer>()
    private var myWndProc: WinUser.WindowProc? = null

    var titleBarHeightPx = 40
    var isMouseOverMinimize = false
    var isMouseOverMaximize = false
    var isMouseOverClose = false

    fun install(window: Window) {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("win")) return

        try {
            val windowId = Native.getWindowID(window)
            val hwnd = HWND(Pointer(windowId))

            if (subclassedWindows.containsKey(hwnd)) {
                return // Already subclassed
            }

            // Query current window styles and add WS_THICKFRAME, WS_MINIMIZEBOX, WS_MAXIMIZEBOX to allow snapping/dragging without caption
            val style = User32.INSTANCE.GetWindowLong(hwnd, User32.GWL_STYLE)
            val newStyle = style or 0x00040000 or 0x00020000 or 0x00010000
            User32.INSTANCE.SetWindowLong(hwnd, User32.GWL_STYLE, newStyle)
            User32.INSTANCE.SetWindowPos(hwnd, null, 0, 0, 0, 0, 0x0027)

            if (myWndProc == null) {
                myWndProc = WinUser.WindowProc { hWnd, uMsg, wParam, lParam ->
                    val originalWndProc = subclassedWindows[hWnd]
                    if (originalWndProc == null) {
                        return@WindowProc User32.INSTANCE.DefWindowProc(hWnd, uMsg, wParam, lParam)
                    }

                    when (uMsg) {
                        WM_CONTEXTMENU -> {
                            val lp = lParam.toLong()
                            val x = (lp and 0xFFFF).toShort().toInt()
                            val y = ((lp shr 16) and 0xFFFF).toShort().toInt()
                            val rect = RECT()
                            User32.INSTANCE.GetWindowRect(hWnd, rect)
                            val clientY = y - rect.top
                            if (clientY < titleBarHeightPx) {
                                LRESULT(0) // Prevent native context menu on title bar
                            } else {
                                DirectUser32.INSTANCE.CallWindowProc(originalWndProc, hWnd, uMsg, wParam, lParam)
                            }
                        }
                        WM_GETMINMAXINFO -> {
                            val mmi = MINMAXINFO(Pointer(lParam.toLong()))
                            val hMonitor = User32.INSTANCE.MonitorFromWindow(hWnd, 2) // MONITOR_DEFAULTTONEAREST
                            if (hMonitor != null) {
                                val monitorInfo = WinUser.MONITORINFO()
                                monitorInfo.cbSize = monitorInfo.size()
                                if (User32.INSTANCE.GetMonitorInfo(hMonitor, monitorInfo).booleanValue()) {
                                    val workWidth = monitorInfo.rcWork.right - monitorInfo.rcWork.left
                                    val workHeight = monitorInfo.rcWork.bottom - monitorInfo.rcWork.top

                                    mmi.ptMaxPosition.x = monitorInfo.rcWork.left - monitorInfo.rcMonitor.left
                                    mmi.ptMaxPosition.y = monitorInfo.rcWork.top - monitorInfo.rcMonitor.top
                                    mmi.ptMaxSize.x = workWidth
                                    mmi.ptMaxSize.y = workHeight
                                    mmi.ptMaxTrackSize.x = workWidth
                                    mmi.ptMaxTrackSize.y = workHeight

                                    mmi.write()
                                }
                            }
                            LRESULT(0)
                        }
                        WM_NCCALCSIZE -> {
                            LRESULT(0)
                        }
                        WM_NCHITTEST -> {
                            val lp = lParam.toLong()
                            val x = (lp and 0xFFFF).toShort().toInt()
                            val y = ((lp shr 16) and 0xFFFF).toShort().toInt()

                            val rect = RECT()
                            User32.INSTANCE.GetWindowRect(hWnd, rect)

                            val border = 6
                            val isLeft = x < rect.left + border
                            val isRight = x >= rect.right - border
                            val isTop = y < rect.top + border
                            val isBottom = y >= rect.bottom - border

                            val hit = when {
                                isTop && isLeft -> HTTOPLEFT
                                isTop && isRight -> HTTOPRIGHT
                                isBottom && isLeft -> HTBOTTOMLEFT
                                isBottom && isRight -> HTBOTTOMRIGHT
                                isLeft -> HTLEFT
                                isRight -> HTRIGHT
                                isTop -> HTTOP
                                isBottom -> HTBOTTOM
                                else -> {
                                    // Let Compose handle dragging and click interactions
                                    HTCLIENT
                                }
                            }
                            LRESULT(hit.toLong())
                        }
                        else -> {
                            DirectUser32.INSTANCE.CallWindowProc(originalWndProc, hWnd, uMsg, wParam, lParam)
                        }
                    }
                }
            }

            val is64 = System.getProperty("os.arch").contains("64")
            val old = if (is64) {
                DirectUser32.INSTANCE.SetWindowLongPtr(hwnd, GWLP_WNDPROC, myWndProc!!)
            } else {
                DirectUser32.INSTANCE.SetWindowLong(hwnd, GWLP_WNDPROC, myWndProc!!)
            }
            subclassedWindows[hwnd] = old
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun uninstall(window: Window) {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("win")) return

        try {
            val windowId = Native.getWindowID(window)
            val hwnd = HWND(Pointer(windowId))
            val oldWndProc = subclassedWindows.remove(hwnd)
            if (oldWndProc != null) {
                val is64 = System.getProperty("os.arch").contains("64")
                if (is64) {
                    DirectUser32.INSTANCE.SetWindowLongPtr(hwnd, GWLP_WNDPROC, oldWndProc)
                } else {
                    DirectUser32.INSTANCE.SetWindowLong(hwnd, GWLP_WNDPROC, oldWndProc)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Structure.FieldOrder("ptReserved", "ptMaxSize", "ptMaxPosition", "ptMinTrackSize", "ptMaxTrackSize")
class MINMAXINFO(p: Pointer) : Structure(p) {
    @JvmField var ptReserved: POINT = POINT()
    @JvmField var ptMaxSize: POINT = POINT()
    @JvmField var ptMaxPosition: POINT = POINT()
    @JvmField var ptMinTrackSize: POINT = POINT()
    @JvmField var ptMaxTrackSize: POINT = POINT()
    init {
        read()
    }
}
