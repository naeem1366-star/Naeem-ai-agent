using System.Collections.Concurrent;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.Net;
using System.Net.Sockets;
using System.Net.NetworkInformation;
using System.Net.WebSockets;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;
using QRCoder;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

namespace NaeemAIDesktopBridge;

public sealed class BridgeForm : Form
{
    private const int Port = 8765;
    private const int DiscoveryPort = 8766;
    private readonly Label _pinLabel = new();
    private readonly Label _fingerprintLabel = new();
    private readonly Label _ipLabel = new();
    private readonly Label _statusLabel = new();
    private readonly PictureBox _pairingQr = new() { Dock = DockStyle.Fill, SizeMode = PictureBoxSizeMode.Zoom, BackColor = Color.White, Margin = new Padding(180, 3, 180, 3) };
    private readonly CheckBox _allowControl = new();
    private readonly Button _copyFingerprint = new();
    private readonly CancellationTokenSource _shutdown = new();
    private readonly string _pin = RandomNumberGenerator.GetInt32(100000, 999999).ToString();
    private readonly string _token = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32)).TrimEnd('=').Replace('+', '-').Replace('/', '_');
    private readonly RSA _certificateKey;
    private readonly X509Certificate2 _certificate;
    private WebApplication? _server;
    private UdpClient? _discovery;
    private string _fingerprint = "";
    private volatile bool _controlEnabled;
    private int _connectedClients;
    private int _pairAttempts;
    private readonly DateTimeOffset _startedAt = DateTimeOffset.UtcNow;

    public BridgeForm()
    {
        Text = "Naeem AI — اتصال امن به کامپیوتر";
        StartPosition = FormStartPosition.CenterScreen;
        MinimumSize = new Size(640, 700);
        Size = new Size(700, 760);
        BackColor = Color.FromArgb(16, 24, 38);
        ForeColor = Color.White;
        Font = new Font("Segoe UI", 10F);

        _certificateKey = RSA.Create(2048);
        var request = new CertificateRequest("CN=Naeem AI Desktop Bridge", _certificateKey, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        request.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, false));
        request.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment, false));
        request.CertificateExtensions.Add(new X509SubjectKeyIdentifierExtension(request.PublicKey, false));
        _certificate = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-5), DateTimeOffset.UtcNow.AddDays(365));
        _fingerprint = Convert.ToHexString(SHA256.HashData(_certificate.RawData));

        var layout = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(24), ColumnCount = 1, RowCount = 12, BackColor = BackColor };
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 48));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 38));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 58));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 38));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 82));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 24));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 158));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 42));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 34));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 48));
        layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 44));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));

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
        layout.Controls.Add(MakeLabel("برای اتصال سریع، این کد را با دوربین گوشی اسکن کن:", 10), 0, 5);
        layout.Controls.Add(_pairingQr, 0, 6);
        _copyFingerprint.Text = "کپی اثر انگشت";
        _copyFingerprint.Dock = DockStyle.Fill;
        _copyFingerprint.BackColor = Color.FromArgb(48, 65, 90);
        _copyFingerprint.ForeColor = Color.White;
        _copyFingerprint.FlatStyle = FlatStyle.Flat;
        _copyFingerprint.Click += (_, _) => { Clipboard.SetText(_fingerprint); _statusLabel.Text = "اثر انگشت کپی شد."; };
        layout.Controls.Add(_copyFingerprint, 0, 7);
        _ipLabel.Text = "نشانی شبکه: " + GetLocalIPv4ForDisplay() + "   |   درگاه: " + Port;
        _ipLabel.Dock = DockStyle.Fill;
        _ipLabel.TextAlign = ContentAlignment.MiddleRight;
        _ipLabel.ForeColor = Color.FromArgb(185, 198, 218);
        layout.Controls.Add(_ipLabel, 0, 8);
        _allowControl.Text = "اجازه کنترل ماوس و صفحه‌کلید از گوشی (برای امنیت، پیش‌فرض خاموش است)";
        _allowControl.Dock = DockStyle.Fill;
        _allowControl.ForeColor = Color.FromArgb(255, 218, 155);
        _allowControl.CheckedChanged += (_, _) => _controlEnabled = _allowControl.Checked;
        layout.Controls.Add(_allowControl, 0, 9);
        _statusLabel.Text = "در حال راه‌اندازی سرویس محلی امن…";
        _statusLabel.Dock = DockStyle.Fill;
        _statusLabel.TextAlign = ContentAlignment.MiddleRight;
        _statusLabel.ForeColor = Color.FromArgb(160, 220, 180);
        layout.Controls.Add(_statusLabel, 0, 10);
        var note = MakeLabel("فقط شبکه محلی • بدون سرور ابری • برای توقف اتصال، این پنجره را ببند.", 8);
        note.Dock = DockStyle.Fill;
        layout.Controls.Add(note, 0, 11);
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

    private void RefreshPairingQr(string ipAddress)
    {
        try
        {
            var payload = JsonSerializer.Serialize(new
            {
                type = "naeem_ai_pc_pair",
                ip = ipAddress,
                port = Port,
                pin = _pin,
                fingerprint = _fingerprint
            });
            using var generator = new QRCodeGenerator();
            using var data = generator.CreateQrCode(payload, QRCodeGenerator.ECCLevel.Q);
            using var qr = new QRCode(data);
            using var bitmap = qr.GetGraphic(7, Color.Black, Color.White, drawQuietZones: true);
            var previous = _pairingQr.Image;
            _pairingQr.Image = new Bitmap(bitmap);
            previous?.Dispose();
        }
        catch (Exception ex)
        {
            _statusLabel.Text = "ساخت کد اتصال ناموفق بود: " + ex.Message;
        }
    }

    private async Task StartServicesAsync()
    {
        try
        {
            var builder = WebApplication.CreateBuilder();
            builder.Logging.ClearProviders();
            var localAddress = IPAddress.Parse(GetLocalIPv4());
            builder.WebHost.ConfigureKestrel(options =>
                options.Listen(IPAddress.Any, Port, listen => listen.UseHttps(_certificate)));
            var app = builder.Build();
            app.UseWebSockets(new WebSocketOptions { KeepAliveInterval = TimeSpan.FromSeconds(20) });

            app.MapGet("/health", () => Results.Json(new { name = "Naeem AI Desktop Bridge", version = "1.0.0", secure = true, controlAllowed = _controlEnabled }));
            app.MapPost("/pair", async (HttpContext context) =>
            {
                using var document = await JsonDocument.ParseAsync(context.Request.Body);
                var suppliedPin = document.RootElement.TryGetProperty("pin", out var pinElement) ? pinElement.GetString() ?? "" : "";
                if (DateTimeOffset.UtcNow - _startedAt > TimeSpan.FromMinutes(15))
                    return Results.Json(new { error = "زمان کد جفت‌سازی تمام شده است؛ برنامه کامپیوتر را دوباره اجرا کن." }, statusCode: 410);
                if (Interlocked.Increment(ref _pairAttempts) > 5)
                    return Results.Json(new { error = "تعداد تلاش‌های جفت‌سازی بیش از حد مجاز است؛ برنامه کامپیوتر را دوباره اجرا کن." }, statusCode: 429);
                if (!CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(suppliedPin), Encoding.UTF8.GetBytes(_pin)))
                    return Results.Json(new { error = "کد جفت‌سازی اشتباه است." }, statusCode: 401);
                return Results.Json(new { token = _token, name = "Naeem AI Desktop", controlAllowed = _controlEnabled });
            });
            app.Map("/ws", HandleWebSocketAsync);

            await app.StartAsync(_shutdown.Token);
            _server = app;
            RefreshPairingQr(localAddress.ToString());
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

        if (Interlocked.CompareExchange(ref _connectedClients, 1, 0) != 0)
        { context.Response.StatusCode = 409; return; }
        using var socket = await context.WebSockets.AcceptWebSocketAsync();
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
                        result = await socket.ReceiveAsync(new ArraySegment<byte>(buffer), receiverCts.Token);
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
                    await socket.SendAsync(new ArraySegment<byte>(frame), WebSocketMessageType.Binary, true, context.RequestAborted);
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
                case "mouse_right_click":
                    RightClickMouse();
                    break;
                case "mouse_scroll":
                    ScrollMouse(root.GetProperty("delta").GetInt32());
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
            using var source = new Bitmap(bounds.Width, bounds.Height);
            using (var graphics = Graphics.FromImage(source))
                graphics.CopyFromScreen(bounds.Location, Point.Empty, bounds.Size);
            using var output = new MemoryStream();
            if (width != bounds.Width || height != bounds.Height)
            {
                using var resized = new Bitmap(source, new Size(width, height));
                resized.Save(output, ImageFormat.Jpeg);
            }
            else source.Save(output, ImageFormat.Jpeg);
            return output.ToArray();
        }
        catch { return Array.Empty<byte>(); }
    }

    private static void MoveMouse(double x, double y)
    {
        var bounds = Screen.PrimaryScreen?.Bounds ?? new Rectangle(0, 0, 1, 1);
        var input = new INPUT { type = INPUT_MOUSE, U = new InputUnion { mi = new MOUSEINPUT {
            dx = (int)(Math.Clamp(x, 0, 1) * 65535), dy = (int)(Math.Clamp(y, 0, 1) * 65535),
            dwFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE
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

    private static void RightClickMouse()
    {
        var inputs = new[] {
            new INPUT { type = INPUT_MOUSE, U = new InputUnion { mi = new MOUSEINPUT { dwFlags = MOUSEEVENTF_RIGHTDOWN } } },
            new INPUT { type = INPUT_MOUSE, U = new InputUnion { mi = new MOUSEINPUT { dwFlags = MOUSEEVENTF_RIGHTUP } } }
        };
        SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<INPUT>());
    }

    private static void ScrollMouse(int delta)
    {
        var input = new INPUT { type = INPUT_MOUSE, U = new InputUnion { mi = new MOUSEINPUT {
            mouseData = unchecked((uint)Math.Clamp(delta, -1200, 1200)),
            dwFlags = MOUSEEVENTF_WHEEL
        } } };
        SendInput(1, new[] { input }, Marshal.SizeOf<INPUT>());
    }

    private static void PressKey(string chord)
    {
        var names = chord.Split('+', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
        var keys = names.Select(name => name.ToUpperInvariant() switch {
            "CTRL" => Keys.ControlKey, "ALT" => Keys.Menu, "SHIFT" => Keys.ShiftKey,
            "WIN" => Keys.LWin, "ENTER" => Keys.Enter, "ESC" => Keys.Escape,
            _ => Enum.TryParse<Keys>(name, true, out var key) ? key : Keys.None
        }).Where(key => key != Keys.None).ToArray();
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
        var normalized = app.ToLowerInvariant();
        if (normalized is not ("blender" or "freecad" or "krita" or "gimp" or "inkscape" or "3dsmax" or "photoshop"))
            return;

        var programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
        var programFilesX86 = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86);
        var localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        var candidates = new List<string>();
        void Add(string path) { if (!string.IsNullOrWhiteSpace(path)) candidates.Add(path); }

        switch (normalized)
        {
            case "blender":
                Add(Path.Combine(programFiles, "Blender Foundation", "Blender", "blender.exe"));
                Add(Path.Combine(localAppData, "Programs", "Blender", "blender.exe"));
                AddRange(Path.Combine(programFiles, "Blender Foundation"), "Blender*", "blender.exe");
                break;
            case "freecad":
                Add(Path.Combine(programFiles, "FreeCAD", "bin", "FreeCAD.exe"));
                try
                {
                    foreach (var directory in Directory.GetDirectories(programFiles, "FreeCAD*"))
                    {
                        Add(Path.Combine(directory, "bin", "FreeCAD.exe"));
                        Add(Path.Combine(directory, "FreeCAD.exe"));
                    }
                }
                catch { }
                break;
            case "krita":
                Add(Path.Combine(programFiles, "Krita (x64)", "bin", "krita.exe"));
                Add(Path.Combine(programFiles, "Krita (x64)", "krita.exe"));
                break;
            case "gimp":
                Add(Path.Combine(programFiles, "GIMP 3", "bin", "gimp-3.0.exe"));
                Add(Path.Combine(programFiles, "GIMP 2", "bin", "gimp-2.10.exe"));
                break;
            case "inkscape":
                Add(Path.Combine(programFiles, "Inkscape", "bin", "inkscape.exe"));
                break;
            case "3dsmax":
                AddRange(programFiles, "Autodesk\\3ds Max*", "3dsmax.exe");
                AddRange(programFilesX86, "Autodesk\\3ds Max*", "3dsmax.exe");
                break;
            case "photoshop":
                AddRange(Path.Combine(programFiles, "Adobe"), "Adobe Photoshop*", "Photoshop.exe");
                AddRange(Path.Combine(programFilesX86, "Adobe"), "Adobe Photoshop*", "Photoshop.exe");
                break;
        }

        var executable = candidates.FirstOrDefault(File.Exists) ??
            (normalized switch {
                "blender" => "blender", "freecad" => "FreeCAD", "krita" => "krita",
                "gimp" => "gimp", "inkscape" => "inkscape", "3dsmax" => "3dsmax",
                "photoshop" => "Photoshop", _ => ""
            });
        if (executable.Length == 0) return;
        try { Process.Start(new ProcessStartInfo(executable) { UseShellExecute = true }); } catch { }

        void AddRange(string root, string directoryPattern, string fileName)
        {
            try
            {
                var parent = directoryPattern.Contains(Path.DirectorySeparatorChar)
                    ? Path.GetDirectoryName(Path.Combine(root, directoryPattern))!
                    : root;
                var pattern = Path.GetFileName(directoryPattern);
                if (!Directory.Exists(parent)) return;
                foreach (var directory in Directory.GetDirectories(parent, pattern).OrderByDescending(x => x))
                    Add(Path.Combine(directory, fileName));
            }
            catch { }
        }
    }

    private void UpdateStatus()
    {
        if (IsDisposed || !IsHandleCreated) return;
        BeginInvoke(new Action(() => _statusLabel.Text = $"سرویس فعال • دستگاه‌های متصل: {_connectedClients} • کنترل: {(_controlEnabled ? "روشن" : "خاموش")}"));
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
        _certificateKey.Dispose();
    }

    private static string GetLocalIPv4()
    {
        var candidates = NetworkInterface.GetAllNetworkInterfaces()
            .Where(adapter => adapter.OperationalStatus == OperationalStatus.Up
                && adapter.NetworkInterfaceType != NetworkInterfaceType.Loopback
                && adapter.NetworkInterfaceType != NetworkInterfaceType.Tunnel)
            .SelectMany(adapter =>
            {
                var properties = adapter.GetIPProperties();
                var hasGateway = properties.GatewayAddresses.Any(gateway =>
                    gateway.Address.AddressFamily == AddressFamily.InterNetwork
                    && !gateway.Address.Equals(IPAddress.Any));
                return properties.UnicastAddresses
                    .Where(item => item.Address.AddressFamily == AddressFamily.InterNetwork
                        && !IPAddress.IsLoopback(item.Address))
                    .Select(item => new
                    {
                        Address = item.Address,
                        IsPrivate = IsPrivateIPv4(item.Address),
                        HasGateway = hasGateway,
                        IsWifi = adapter.NetworkInterfaceType == NetworkInterfaceType.Wireless80211
                    });
            })
            .Where(item => item.IsPrivate)
            .OrderByDescending(item => item.IsWifi && item.HasGateway)
            .ThenByDescending(item => item.HasGateway)
            .ThenByDescending(item => item.IsWifi)
            .ToArray();

        return candidates.FirstOrDefault()?.Address.ToString()
            ?? throw new InvalidOperationException("آدرس شبکه محلی پیدا نشد. کامپیوتر را به Wi-Fi هات‌اسپات گوشی وصل کن و برنامه را دوباره اجرا کن.");
    }

    private static bool IsPrivateIPv4(IPAddress address)
    {
        var bytes = address.GetAddressBytes();
        return bytes[0] == 10 || (bytes[0] == 192 && bytes[1] == 168)
            || (bytes[0] == 172 && bytes[1] >= 16 && bytes[1] <= 31);
    }

    private static string GetLocalIPv4ForDisplay()
    {
        try { return GetLocalIPv4(); }
        catch { return "هنوز به شبکه محلی وصل نیست"; }
    }

    private const int INPUT_MOUSE = 0, INPUT_KEYBOARD = 1;
    private const uint MOUSEEVENTF_MOVE = 0x0001, MOUSEEVENTF_LEFTDOWN = 0x0002, MOUSEEVENTF_LEFTUP = 0x0004,
        MOUSEEVENTF_RIGHTDOWN = 0x0008, MOUSEEVENTF_RIGHTUP = 0x0010, MOUSEEVENTF_WHEEL = 0x0800,
        MOUSEEVENTF_ABSOLUTE = 0x8000, KEYEVENTF_KEYUP = 0x0002, KEYEVENTF_UNICODE = 0x0004;
    [StructLayout(LayoutKind.Sequential)] private struct INPUT { public int type; public InputUnion U; }
    [StructLayout(LayoutKind.Explicit)] private struct InputUnion { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }
    [StructLayout(LayoutKind.Sequential)] private struct MOUSEINPUT { public int dx, dy; public uint mouseData, dwFlags, time; public IntPtr dwExtraInfo; }
    [StructLayout(LayoutKind.Sequential)] private struct KEYBDINPUT { public ushort wVk, wScan; public uint dwFlags, time; public IntPtr dwExtraInfo; }
    [DllImport("user32.dll", SetLastError = true)] private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);
}
