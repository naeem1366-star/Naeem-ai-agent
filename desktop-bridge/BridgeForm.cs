using System.Collections.Concurrent;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;

namespace NaeemAIDesktopBridge;

public sealed class BridgeForm : Form
{
    private const int Port = 8765;
    private const int DiscoveryPort = 8766;
    private readonly Label _pinLabel = new();
    private readonly Label _fingerprintLabel = new();
    private readonly Label _ipLabel = new();
    private readonly Label _statusLabel = new();
    private readonly CheckBox _allowControl = new();
    private readonly Button _copyFingerprint = new();
    private readonly CancellationTokenSource _shutdown = new();
    private readonly string _pin = RandomNumberGenerator.GetInt32(100000, 999999).ToString();
    private readonly string _token = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32)).TrimEnd('=').Replace('+', '-').Replace('/', '_');
    private readonly X509Certificate2 _certificate;
    private WebApplication? _server;
    private UdpClient? _discovery;
    private string _fingerprint = "";
    private volatile bool _controlEnabled;
    private int _connectedClients;

    public BridgeForm()
    {
        Text = "Naeem AI — اتصال امن به کامپیوتر";
        StartPosition = FormStartPosition.CenterScreen;
        MinimumSize = new Size(620, 500);
        Size = new Size(700, 560);
        BackColor = Color.FromArgb(16, 24, 38);
        ForeColor = Color.White;
        Font = new Font("Segoe UI", 10F);

        using var rsa = RSA.Create(2048);
        var request = new CertificateRequest("CN=Naeem AI Desktop Bridge", rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        request.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, false));
        request.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment, false));
        request.CertificateExtensions.Add(new X509SubjectKeyIdentifierExtension(request.PublicKey, false));
        _certificate = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-5), DateTimeOffset.UtcNow.AddDays(365));
        _fingerprint = Convert.ToHexString(SHA256.HashData(_certificate.RawData));

        var layout = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(28), ColumnCount = 1, RowCount = 10, BackColor = BackColor };
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 54));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 48));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 66));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 48));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 100));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 52));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 42));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 48));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 20));

        var title = new Label { Text = "اتصال گوشی به کامپیوتر", Dock = DockStyle.Fill, Font = new Font("Segoe UI", 18F, FontStyle.Bold), ForeColor = Color.FromArgb(225, 232, 242), TextAlign = ContentAlignment.MiddleRight };
        layout.Controls.Add(title, 0, 0);
        layout.Controls.Add(MakeLabel("کد جفت‌سازی (در برنامه گوشی وارد کن):", 10), 0, 1);
        _pinLabel.Text = _pin;
        _pinLabel.Dock = DockStyle.Fill;
        _pinLabel.Font = new Font("Consolas", 25F, FontStyle.Bold);
        _pinLabel.ForeColor = Color.FromArgb(150, 195, 255);
        _pinLabel.TextAlign = ContentAlignment.MiddleCenter;
        layout.Controls.Add(_pinLabel, 0, 2);
        layout.Controls.Add(MakeLabel("اثر انگشت گواهی امنیتی (برای تأیید همین کامپیوتر):", 10), 0, 3);
        _fingerprintLabel.Text = FormatFingerprint(_fingerprint);
        _fingerprintLabel.Dock = DockStyle.Fill;
        _fingerprintLabel.Font = new Font("Consolas", 10F);
        _fingerprintLabel.ForeColor = Color.FromArgb(200, 210, 225);
        _fingerprintLabel.TextAlign = ContentAlignment.MiddleCenter;
        layout.Controls.Add(_fingerprintLabel, 0, 4);
        _copyFingerprint.Text = "کپی اثر انگشت";
        _copyFingerprint.Dock = DockStyle.Fill;
        _copyFingerprint.BackColor = Color.FromArgb(48, 65, 90);
        _copyFingerprint.ForeColor = Color.White;
        _copyFingerprint.FlatStyle = FlatStyle.Flat;
        _copyFingerprint.Click += (_, _) => { Clipboard.SetText(_fingerprint); _statusLabel.Text = "اثر انگشت کپی شد."; };
        layout.Controls.Add(_copyFingerprint, 0, 5);
        _ipLabel.Text = "نشانی شبکه: " + GetLocalIPv4() + "   |   درگاه: " + Port;
        _ipLabel.Dock = DockStyle.Fill;
        _ipLabel.TextAlign = ContentAlignment.MiddleRight;
        _ipLabel.ForeColor = Color.FromArgb(185, 198, 218);
        layout.Controls.Add(_ipLabel, 0, 6);
        _allowControl.Text = "اجازه کنترل ماوس و صفحه‌کلید از گوشی (برای امنیت، پیش‌فرض خاموش است)";
        _allowControl.Dock = DockStyle.Fill;
        _allowControl.ForeColor = Color.FromArgb(255, 218, 155);
        _allowControl.CheckedChanged += (_, _) => _controlEnabled = _allowControl.Checked;
        layout.Controls.Add(_allowControl, 0, 7);
        _statusLabel.Text = "در حال راه‌اندازی سرویس محلی امن…";
        _statusLabel.Dock = DockStyle.Fill;
        _statusLabel.TextAlign = ContentAlignment.MiddleRight;
        _statusLabel.ForeColor = Color.FromArgb(160, 220, 180);
        layout.Controls.Add(_statusLabel, 0, 8);
        var note = MakeLabel("فقط شبکه محلی • بدون سرور ابری • برای توقف اتصال، این پنجره را ببند.", 8);
        note.Dock = DockStyle.Fill;
        layout.Controls.Add(note, 0, 9);
        Controls.Add(layout);

        Shown += async (_, _) => await StartServicesAsync();
        FormClosing += async (_, _) => await StopServicesAsync();
    }

    private static Label MakeLabel(string text, float size) => new()
    {
        Text = text, Dock = DockStyle.Fill, TextAlign = ContentAlignment.MiddleRight,
        ForeColor = Color.FromArgb(185, 198, 218), Font = new Font("Segoe UI", size)
    };

    private static string FormatFingerprint(string value) => string.Join(":", Enumerable.Range(0, value.Length / 2).Select(i => value.Substring(i * 2, 2)));

    private async Task StartServicesAsync()
    {
        try
        {
            var builder = WebApplication.CreateBuilder();
            builder.Logging.ClearProviders();
            builder.WebHost.ConfigureKestrel(options =>
                options.Listen(IPAddress.Any, Port, listen => listen.UseHttps(_certificate)));
            var app = builder.Build();
            app.UseWebSockets(new WebSocketOptions { KeepAliveInterval = TimeSpan.FromSeconds(20) });

            app.MapGet("/health", () => Results.Json(new { name = "Naeem AI Desktop Bridge", version = "1.0.0", secure = true }));
            app.MapPost("/pair", async (HttpContext context) =>
            {
                using var document = await JsonDocument.ParseAsync(context.Request.Body);
                var suppliedPin = document.RootElement.TryGetProperty("pin", out var pinElement) ? pinElement.GetString() ?? "" : "";
                if (!CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(suppliedPin), Encoding.UTF8.GetBytes(_pin)))
                    return Results.Json(new { error = "کد جفت‌سازی اشتباه است." }, statusCode: 401);
                return Results.Json(new { token = _token, name = "Naeem AI Desktop", controlAllowed = _controlEnabled });
            });
            app.Map("/ws", HandleWebSocketAsync);

            await app.StartAsync(_shutdown.Token);
            _server = app;
            _discovery = new UdpClient(DiscoveryPort) { EnableBroadcast = true };
            _ = Task.Run(() => DiscoveryLoopAsync(_shutdown.Token));
            _statusLabel.Text = "سرویس فعال است. گوشی را به همین مودم/شبکه وصل کن و «جست‌وجوی کامپیوتر» را بزن.";
        }
        catch (Exception ex)
        {
            _statusLabel.Text = "راه‌اندازی ناموفق: " + ex.Message;
            MessageBox.Show(this, ex.ToString(), "خطای راه‌اندازی", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private async Task DiscoveryLoopAsync(CancellationToken cancellationToken)
    {
        if (_discovery is null) return;
        while (!cancellationToken.IsCancellationRequested)
        {
            try
            {
                var packet = await _discovery.ReceiveAsync(cancellationToken);
                var message = Encoding.UTF8.GetString(packet.Buffer);
                if (message != "NAEEM_BRIDGE_DISCOVER") continue;
                var payload = JsonSerializer.Serialize(new { name = "Naeem AI Desktop", ip = GetLocalIPv4(), port = Port, fingerprint = _fingerprint });
                var bytes = Encoding.UTF8.GetBytes(payload);
                await _discovery.SendAsync(bytes, packet.RemoteEndPoint, cancellationToken);
            }
            catch (OperationCanceledException) { break; }
            catch (ObjectDisposedException) { break; }
            catch { await Task.Delay(300, cancellationToken).ContinueWith(_ => { }); }
        }
    }

    private async Task HandleWebSocketAsync(HttpContext context)
    {
        if (!context.WebSockets.IsWebSocketRequest) { context.Response.StatusCode = 400; return; }
        var suppliedToken = context.Request.Query["token"].ToString();
        if (!CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(suppliedToken), Encoding.UTF8.GetBytes(_token)))
        { context.Response.StatusCode = 401; return; }

        using var socket = await context.WebSockets.AcceptWebSocketAsync();
        Interlocked.Increment(ref _connectedClients);
        UpdateStatus();
        var incoming = new ConcurrentQueue<string>();
        using var receiverCts = CancellationTokenSource.CreateLinkedTokenSource(context.RequestAborted);
        var receiver = Task.Run(async () =>
        {
            var buffer = new byte[16 * 1024];
            try
            {
                while (socket.State == WebSocketState.Open && !receiverCts.IsCancellationRequested)
                {
                    using var stream = new MemoryStream();
                    WebSocketReceiveResult result;
                    do
                    {
                        result = await socket.ReceiveAsync(buffer, receiverCts.Token);
                        if (result.MessageType == WebSocketMessageType.Close) return;
                        if (result.MessageType == WebSocketMessageType.Text) stream.Write(buffer, 0, result.Count);
                    } while (!result.EndOfMessage);
                    if (stream.Length > 64 * 1024) continue;
                    incoming.Enqueue(Encoding.UTF8.GetString(stream.ToArray()));
                }
            }
            catch { }
        }, receiverCts.Token);

        try
        {
            while (socket.State == WebSocketState.Open && !context.RequestAborted.IsCancellationRequested)
            {
                while (incoming.TryDequeue(out var command)) ProcessCommand(command);
                var frame = CaptureScreenJpeg();
                if (frame.Length > 0)
                    await socket.SendAsync(frame, WebSocketMessageType.Binary, true, context.RequestAborted);
                await Task.Delay(500, context.RequestAborted);
            }
        }
        catch { }
        finally
        {
            receiverCts.Cancel();
            try { await receiver; } catch { }
            Interlocked.Decrement(ref _connectedClients);
            UpdateStatus();
        }
    }

    private void ProcessCommand(string json)
    {
        try
        {
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;
            var type = root.TryGetProperty("type", out var typeElement) ? typeElement.GetString() : "";
            if (type == "ping") return;
            if (!_controlEnabled) return;
            switch (type)
            {
                case "mouse_move":
                    MoveMouse(root.GetProperty("x").GetDouble(), root.GetProperty("y").GetDouble());
                    break;
                case "mouse_click":
                    ClickMouse();
                    break;
                case "key":
                    PressKey(root.GetProperty("key").GetString() ?? "");
                    break;
                case "type_text":
                    TypeText(root.GetProperty("text").GetString() ?? "");
                    break;
                case "launch_app":
                    LaunchAllowedApp(root.GetProperty("app").GetString() ?? "");
                    break;
            }
        }
        catch { }
    }

    private static byte[] CaptureScreenJpeg()
    {
        try
        {
            var bounds = Screen.PrimaryScreen?.Bounds ?? throw new InvalidOperationException("No primary screen");
            var scale = Math.Min(1.0, 1280.0 / bounds.Width);
            var width = Math.Max(1, (int)(bounds.Width * scale));
            var height = Math.Max(1, (int)(bounds.Height * scale));
            using var bitmap = new Bitmap(width, height);
            using (var graphics = Graphics.FromImage(bitmap))
            {
                graphics.CopyFromScreen(bounds.Left, bounds.Top, 0, 0, bounds.Size);
                if (width != bounds.Width || height != bounds.Height)
                {
                    using var resized = new Bitmap(bitmap, new Size(width, height));
                    using var output = new MemoryStream();
                    resized.Save(output, ImageFormat.Jpeg);
                    return output.ToArray();
                }
            }
            using var stream = new MemoryStream();
            bitmap.Save(stream, ImageFormat.Jpeg);
            return stream.ToArray();
        }
        catch { return Array.Empty<byte>(); }
    }

    private static void MoveMouse(double x, double y)
    {
        var bounds = Screen.PrimaryScreen?.Bounds ?? new Rectangle(0, 0, 1, 1);
        var input = new INPUT { type = INPUT_MOUSE, U = new InputUnion { mi = new MOUSEINPUT {
            dx = (int)(Math.Clamp(x, 0, 1) * 65535), dy = (int)(Math.Clamp(y, 0, 1) * 65535),
            dwFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_VIRTUALDESK
        } } };
        SendInput(1, new[] { input }, Marshal.SizeOf<INPUT>());
    }

    private static void ClickMouse()
    {
        var inputs = new[] {
            new INPUT { type = INPUT_MOUSE, U = new InputUnion { mi = new MOUSEINPUT { dwFlags = MOUSEEVENTF_LEFTDOWN } } },
            new INPUT { type = INPUT_MOUSE, U = new InputUnion { mi = new MOUSEINPUT { dwFlags = MOUSEEVENTF_LEFTUP } } }
        };
        SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<INPUT>());
    }

    private static void PressKey(string chord)
    {
        var names = chord.Split('+', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
        var keys = names.Select(name => Enum.TryParse<Keys>(name, true, out var key) ? key : Keys.None).Where(key => key != Keys.None).ToArray();
        if (keys.Length == 0) return;
        var inputs = new List<INPUT>();
        foreach (var key in keys) inputs.Add(KeyInput(key, false));
        foreach (var key in keys.Reverse()) inputs.Add(KeyInput(key, true));
        SendInput((uint)inputs.Count, inputs.ToArray(), Marshal.SizeOf<INPUT>());
    }

    private static void TypeText(string text)
    {
        var inputs = new List<INPUT>();
        foreach (var character in text.Take(2000))
        {
            inputs.Add(new INPUT { type = INPUT_KEYBOARD, U = new InputUnion { ki = new KEYBDINPUT { wScan = character, dwFlags = KEYEVENTF_UNICODE } } });
            inputs.Add(new INPUT { type = INPUT_KEYBOARD, U = new InputUnion { ki = new KEYBDINPUT { wScan = character, dwFlags = KEYEVENTF_UNICODE | KEYEVENTF_KEYUP } } });
        }
        if (inputs.Count > 0) SendInput((uint)inputs.Count, inputs.ToArray(), Marshal.SizeOf<INPUT>());
    }

    private static INPUT KeyInput(Keys key, bool keyUp)
    {
        var virtualKey = (ushort)key;
        return new INPUT { type = INPUT_KEYBOARD, U = new InputUnion { ki = new KEYBDINPUT { wVk = virtualKey, dwFlags = keyUp ? KEYEVENTF_KEYUP : 0 } } };
    }

    private static void LaunchAllowedApp(string app)
    {
        var executable = app.ToLowerInvariant() switch {
            "blender" => "blender",
            "freecad" => "FreeCAD",
            "krita" => "krita",
            "gimp" => "gimp",
            "inkscape" => "inkscape",
            _ => ""
        };
        if (executable.Length == 0) return;
        try { Process.Start(new ProcessStartInfo(executable) { UseShellExecute = true }); } catch { }
    }

    private void UpdateStatus()
    {
        if (IsDisposed || !IsHandleCreated) return;
        BeginInvoke(() => _statusLabel.Text = $"سرویس فعال • دستگاه‌های متصل: {_connectedClients} • کنترل: {(_controlEnabled ? "روشن" : "خاموش")}");
    }

    private async Task StopServicesAsync()
    {
        _shutdown.Cancel();
        _discovery?.Dispose();
        if (_server is not null)
        {
            try { await _server.StopAsync(TimeSpan.FromSeconds(2)); await _server.DisposeAsync(); } catch { }
        }
        _certificate.Dispose();
    }

    private static string GetLocalIPv4()
    {
        foreach (var address in Dns.GetHostEntry(Dns.GetHostName()).AddressList)
            if (address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(address))
                return address.ToString();
        return "IP را از تنظیمات شبکه ویندوز بررسی کن";
    }

    private const int INPUT_MOUSE = 0, INPUT_KEYBOARD = 1;
    private const uint MOUSEEVENTF_MOVE = 0x0001, MOUSEEVENTF_LEFTDOWN = 0x0002, MOUSEEVENTF_LEFTUP = 0x0004,
        MOUSEEVENTF_ABSOLUTE = 0x8000, MOUSEEVENTF_VIRTUALDESK = 0x4000, KEYEVENTF_KEYUP = 0x0002, KEYEVENTF_UNICODE = 0x0004;
    [StructLayout(LayoutKind.Sequential)] private struct INPUT { public int type; public InputUnion U; }
    [StructLayout(LayoutKind.Explicit)] private struct InputUnion { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }
    [StructLayout(LayoutKind.Sequential)] private struct MOUSEINPUT { public int dx, dy; public uint mouseData, dwFlags, time; public IntPtr dwExtraInfo; }
    [StructLayout(LayoutKind.Sequential)] private struct KEYBDINPUT { public ushort wVk, wScan; public uint dwFlags, time; public IntPtr dwExtraInfo; }
    [DllImport("user32.dll", SetLastError = true)] private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);
}
