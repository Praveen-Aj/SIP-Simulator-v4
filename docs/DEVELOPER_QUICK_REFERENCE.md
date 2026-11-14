# 🛠️ SIP Simulator - Developer Quick Reference

## 🔄 **Making Updates & Creating New Installers**

---

## ⚡ **Quick Commands**

### **After Making Code Changes:**

```batch
# ONE COMMAND - Does everything:
.\DEPLOY_INSTALLER.bat
```

**Output:** `installer_output\SIP_Simulator_Setup_v1.0.exe`

**Time:** ~5 minutes

---

## 📋 **Step-by-Step Workflow**

### **1. Make Your Code Changes**
- Edit Java files in `src/sipinspector/`
- Test locally with `.\run.bat`

### **2. Create New Installer**
```batch
.\DEPLOY_INSTALLER.bat
```

### **3. Test the Installer**
- Run `installer_output\SIP_Simulator_Setup_v1.0.exe`
- Verify desktop shortcut works
- Test all features

### **4. Distribute to Team**
- Share `SIP_Simulator_Setup_v1.0.exe`
- Include `INSTALLATION_GUIDE_FOR_USERS.md`

---

## 🔧 **Individual Build Steps**

If you need more control:

### **Step 1: Build Application**
```batch
.\build.bat
```
**Output:** `SIP_Simulator.jar`

### **Step 2: Create EXE (if needed)**
```batch
.\2_create_exe.bat
```
**Output:** `SIP_Simulator.exe`

### **Step 3: Create Installer**
```batch
.\5_create_installer.bat
```
**Output:** `installer_output\SIP_Simulator_Setup_v1.0.exe`

---

## 📁 **Key Files**

### **Build Scripts:**
- `build.bat` - Compiles Java code
- `DEPLOY_INSTALLER.bat` - Master script (use this!)
- `2_create_exe.bat` - Creates Windows EXE
- `5_create_installer.bat` - Creates installer

### **Configuration:**
- `launch4j_config.xml` - EXE wrapper settings
- `installer_script.iss` - Installer settings

### **Output:**
- `SIP_Simulator.jar` - Java application
- `SIP_Simulator.exe` - Windows executable
- `SIP_Simulator.ico` - Application icon
- `installer_output/SIP_Simulator_Setup_v1.0.exe` - Installer

---

## 🎨 **Updating Version Number**

### **1. Update Version in Installer Script:**

Edit `installer_script.iss`:
```ini
#define MyAppVersion "1.1"  ← Change this
```

### **2. Update Version in Launch4j Config:**

Edit `launch4j_config.xml`:
```xml
<fileVersion>1.1.0.0</fileVersion>
<txtFileVersion>1.1.0</txtFileVersion>
<productVersion>1.1.0.0</productVersion>
<txtProductVersion>1.1.0</txtProductVersion>
```

### **3. Rebuild:**
```batch
.\DEPLOY_INSTALLER.bat
```

**New Output:** `SIP_Simulator_Setup_v1.1.exe`

---

## 🐛 **Common Issues & Fixes**

### **Issue: Build fails**
```batch
# Clean and rebuild:
rmdir /s /q build
.\build.bat
```

### **Issue: EXE creation fails**
```batch
# Check icon file exists:
dir SIP_Simulator.ico

# Recreate icon if missing:
.\create_icon.bat
```

### **Issue: Installer fails**
```batch
# Check Inno Setup installed:
dir "C:\Program Files (x86)\Inno Setup 6"

# Reinstall if needed:
.\4_setup_inno_setup.bat
```

---

## 📦 **What Gets Packaged**

The installer includes:

- ✅ `SIP_Simulator.exe` - Main application
- ✅ `jre/` - Java Runtime (40 MB)
- ✅ `lib/` - All JAR libraries
- ✅ `scenarios/` - SIP scenarios
- ✅ `Icons/` - UI icons
- ✅ `security/` - TLS certificates
- ✅ `fonts/` - Emoji fonts
- ✅ `README.txt` - Documentation

**Total Size:** ~31 MB compressed

---

## 🔍 **Testing Checklist**

Before distributing:

### **Local Testing:**
- [ ] Run `.\run.bat` - App launches
- [ ] All menus work
- [ ] Fonts look clean (Segoe UI)
- [ ] UDP/TCP/TLS transports work
- [ ] Scenarios load correctly

### **Installer Testing:**
- [ ] Run installer
- [ ] Desktop shortcut created
- [ ] Desktop shortcut works
- [ ] Start Menu entry created
- [ ] App launches from shortcut
- [ ] All features work
- [ ] Uninstaller works

### **Clean Machine Testing:**
- [ ] Test on machine without Java
- [ ] Test on different Windows version
- [ ] Test in different installation path

---

## 📝 **Distribution Checklist**

When sharing with team:

- [ ] Build new installer
- [ ] Test installer yourself
- [ ] Update version number (if needed)
- [ ] Include `INSTALLATION_GUIDE_FOR_USERS.md`
- [ ] Share installer file
- [ ] Provide installation instructions
- [ ] Be available for support

---

## 🚀 **Quick Distribution**

### **Option 1: Shared Network Drive**
```
1. Copy installer to shared drive
2. Send email with location
3. Include installation guide
```

### **Option 2: Email (if size allows)**
```
1. Compress installer (optional)
2. Attach to email
3. Include installation instructions
```

### **Option 3: Cloud Storage**
```
1. Upload to OneDrive/Google Drive
2. Share link with team
3. Include installation guide
```

---

## 💡 **Pro Tips**

### **Tip 1: Keep Old Versions**
```batch
# Rename before creating new:
move installer_output\SIP_Simulator_Setup_v1.0.exe installer_output\SIP_Simulator_Setup_v1.0_backup.exe
```

### **Tip 2: Automated Builds**
```batch
# Create a daily build script:
@echo off
set VERSION=%date:~-4%%date:~-7,2%%date:~-10,2%
.\DEPLOY_INSTALLER.bat
ren installer_output\SIP_Simulator_Setup_v1.0.exe SIP_Simulator_Setup_%VERSION%.exe
```

### **Tip 3: Quick Test**
```batch
# Test without installing:
.\run.bat
```

---

## 📊 **File Sizes Reference**

- `SIP_Simulator.jar` - ~500 KB
- `SIP_Simulator.exe` - ~1.2 MB
- `deployment/jre/` - ~40 MB
- `installer_output/SIP_Simulator_Setup_v1.0.exe` - ~31 MB

---

## 🔄 **Update Workflow Example**

### **Scenario: Bug Fix**

```batch
# 1. Fix code
# Edit src/sipinspector/SomeFile.java

# 2. Test locally
.\run.bat

# 3. Create new installer
.\DEPLOY_INSTALLER.bat

# 4. Test installer
installer_output\SIP_Simulator_Setup_v1.0.exe

# 5. Distribute
# Share with team
```

**Time:** 10-15 minutes total

---

## 📞 **Support**

### **If Build Fails:**
1. Check error messages
2. Verify all files present
3. Clean and rebuild
4. Check Java version (Java 8)

### **If Installer Fails:**
1. Check Inno Setup installed
2. Verify all source files present
3. Check `installer_script.iss` syntax
4. Review error messages

---

## ✅ **Summary**

### **For Regular Updates:**
```batch
.\DEPLOY_INSTALLER.bat
```

### **Output:**
```
installer_output\SIP_Simulator_Setup_v1.0.exe
```

### **Share With:**
- Installer file
- `INSTALLATION_GUIDE_FOR_USERS.md`

**That's it!** 🎉

---

**Quick Reference Version:** 1.0  
**Last Updated:** November 2025
