using System;

using System.IO;
using System.Reflection;

using System.Windows.Forms;
using Microsoft.Win32;

static class Setup {
    const string Name = "Bluetooth Console";
    const string Page = "HC-05.html";
    const string Icon = "Bluetooth Console PCB.ico";
    const string RegistryPath = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\BluetoothConsole";

    [STAThread]
    static int Main(string[] args) {
        try {
            if (args.Length == 1 && args[0] == "--verify") {
                foreach (var name in new[]{"console.html","console.ico"})
                    using (var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(name))
                        if (stream == null || stream.Length == 0) return 2;
                return 0;
            }

            Install();
            return 0;
        } catch (Exception error) {
            MessageBox.Show(error.Message, Name, MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }

    static string RegisteredFolder() {
        using (var key = Registry.CurrentUser.OpenSubKey(RegistryPath))
            return key == null ? null : key.GetValue("InstallLocation") as string;
    }

    static string[] Shortcuts(string folder) {
        return new[]{
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), Name + ".lnk"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), Name + ".lnk"),
            Path.Combine(folder, Name + ".lnk")
        };
    }

    static void Install() {
        string edge = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86),
                                   @"Microsoft\Edge\Application\msedge.exe");
        if (!File.Exists(edge)) throw new InvalidOperationException("请先安装 Microsoft Edge。");
        string defaultFolder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), Name);
        string previousFolder = RegisteredFolder() ?? defaultFolder;
        string folder;
        using (var picker = new FolderBrowserDialog()) {
            picker.Description = "选择安装位置。安装程序会使用此位置下的 Bluetooth Console 文件夹。";
            picker.SelectedPath = Directory.Exists(previousFolder) ? previousFolder : Path.GetDirectoryName(defaultFolder);
            picker.ShowNewFolderButton = true;
            if (picker.ShowDialog() != DialogResult.OK) return;
            string selected = Path.GetFullPath(picker.SelectedPath);
            folder = String.Equals(Path.GetFileName(selected.TrimEnd(Path.DirectorySeparatorChar)), Name, StringComparison.OrdinalIgnoreCase)
                ? selected : Path.Combine(selected, Name);
        }
        if (MessageBox.Show("安装位置\n" + folder, Name, MessageBoxButtons.OKCancel) != DialogResult.OK) return;
        Directory.CreateDirectory(folder);
        string page = Path.Combine(folder, Page);
        string icon = Path.Combine(folder, Icon);
        string legacyUninstaller = Path.Combine(folder, "Uninstall.exe");
        Extract("console.html",page);
        Extract("console.ico",icon);


        dynamic shell = Activator.CreateInstance(Type.GetTypeFromProgID("WScript.Shell"));
        foreach (var path in Shortcuts(folder)) {
            dynamic link = shell.CreateShortcut(path);
            link.TargetPath = edge;
            link.Arguments = "--app=\"" + new Uri(page).AbsoluteUri + "\"";
            link.IconLocation = icon + ",0";
            link.Save();
        }
        if (String.Equals(folder, RegisteredFolder(), StringComparison.OrdinalIgnoreCase)) {
            File.Delete(legacyUninstaller);
            Registry.CurrentUser.DeleteSubKeyTree(RegistryPath, false);
        }
        string message = "安装完成。\n" + folder;
        if (!String.Equals(folder, previousFolder, StringComparison.OrdinalIgnoreCase) &&
            File.Exists(Path.Combine(previousFolder, Page)))
            message += "\n旧位置的文件仍可用于导出原配置。\n" + previousFolder;
        MessageBox.Show(message, Name);
    }

    static void Extract(string resource,string destination) {
        using (var input = Assembly.GetExecutingAssembly().GetManifestResourceStream(resource))
        using (var output = File.Create(destination))
            input.CopyTo(output);
    }
}



