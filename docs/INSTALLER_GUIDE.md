# 🎯 SIP Simulator - Professional Windows Installer Guide

## ✅ **What You Get**

A **professional Windows installer** that:
- ✅ Looks like any commercial software installer
- ✅ Creates **desktop shortcut automatically**
- ✅ Creates Start Menu entry
- ✅ Installs to Program Files
- ✅ Includes uninstaller
- ✅ Professional installation wizard

---

## 🚀 **Quick Start - ONE COMMAND**

```batch
DEPLOY_INSTALLER.bat
```

**That's it!** Wait 15-20 minutes (first time), and you'll have:
- `SIP_Simulator_Setup_v1.0.exe` - Professional installer

---

## 📦 **What Your Team Gets**

### **Installation Experience:**

```
1. Double-click: SIP_Simulator_Setup_v1.0.exe
2. See: Professional installation wizard
3. Click: "Next" → "Next" → "Install"
4. Done: Desktop shortcut created automatically!
```

### **After Installation:**
- 🖥️ **Desktop shortcut** - Just double-click to launch!
- 📁 **Start Menu entry** - Find in Start Menu
- 📂 **Installed in:** `C:\Program Files\SIP Simulator\`
- 🗑️ **Uninstaller** - In Start Menu or Control Panel

---

## 🎨 **Font Improvements**

### **Updated Font System:**
Now uses **Segoe UI** (Windows 10/11 modern font):
- ✅ Clean, professional appearance
- ✅ Better readability
- ✅ Modern look
- ✅ Consistent with Windows UI

### **Font Priority:**
1. **Segoe UI** - Modern Windows font (clean!)
2. Roboto - Google font
3. Calibri - Office font
4. Tahoma - Classic Windows
5. Verdana - Web-safe
6. Arial - Universal fallback

**Result:** Much cleaner, more professional appearance!

---

## 📋 **Deployment Steps**

### **Option 1: Automatic (Recommended)**

```batch
DEPLOY_INSTALLER.bat
```

Does everything automatically:
1. Builds application
2. Downloads tools (if needed)
3. Creates EXE
4. Downloads Inno Setup
5. Creates installer

---

### **Option 2: Manual (Step by Step)**

#### **Step 1: Build Application**
```batch
build.bat
```

#### **Step 2: Setup Deployment Tools**
```batch
1_setup_deployment_tools.bat
```
Downloads Launch4j and JRE (~45 MB)

#### **Step 3: Create EXE**
```batch
2_create_exe.bat
```
Creates `SIP_Simulator.exe`

#### **Step 4: Setup Inno Setup**
```batch
4_setup_inno_setup.bat
```
Downloads and installs Inno Setup (~3 MB)

#### **Step 5: Create Installer**
```batch
5_create_installer.bat
```
Creates `SIP_Simulator_Setup_v1.0.exe`

---

## 🎯 **Installer Features**

### **What the Installer Does:**

1. **Welcome Screen**
   - Professional branding
   - Version information

2. **License Agreement**
   - Optional (can be added)

3. **Installation Location**
   - Default: `C:\Program Files\SIP Simulator\`
   - User can change

4. **Select Components**
   - Desktop shortcut (checked by default)
   - Quick Launch icon (optional)

5. **Installing**
   - Progress bar
   - Copies all files
   - Creates shortcuts

6. **Finish**
   - Option to launch immediately
   - Desktop shortcut created!

### **What Gets Installed:**

```
C:\Program Files\SIP Simulator\
├── SIP_Simulator.exe          ← Main application
├── jre\                       ← Java Runtime (bundled)
├── lib\                       ← Libraries
├── scenarios\                 ← SIP scenarios
├── Icons\                     ← UI icons
├── security\                  ← TLS certificates
├── fonts\                     ← Fonts
├── logs\                      ← Log files (created)
└── README.txt                 ← Documentation
```

### **Shortcuts Created:**

- 🖥️ **Desktop:** `SIP Simulator` icon
- 📁 **Start Menu:** `SIP Simulator` folder with:
  - SIP Simulator (launch app)
  - README (documentation)
  - Uninstall (remove app)

---

## 🧪 **Testing the Installer**

### **Before Distribution:**

1. **Run the installer:**
   ```
   Double-click: installer_output\SIP_Simulator_Setup_v1.0.exe
   ```

2. **Verify installation:**
   - [ ] Installation wizard appears
   - [ ] Can choose installation location
   - [ ] Desktop shortcut option shown
   - [ ] Installation completes successfully

3. **Verify shortcuts:**
   - [ ] Desktop shortcut created
   - [ ] Desktop shortcut works (double-click launches app)
   - [ ] Start Menu entry created
   - [ ] Start Menu entry works

4. **Verify application:**
   - [ ] App launches from desktop shortcut
   - [ ] Window title: "SIP Simulator"
   - [ ] Menus display correctly
   - [ ] Fonts look clean and professional
   - [ ] All features work

5. **Verify uninstaller:**
   - [ ] Uninstaller in Start Menu
   - [ ] Uninstaller works
   - [ ] Desktop shortcut removed
   - [ ] Files removed from Program Files

---

## 📊 **Comparison: Installer vs Portable**

### **Windows Installer (NEW):**
✅ Professional installation wizard
✅ Desktop shortcut (automatic)
✅ Start Menu entry
✅ Installs to Program Files
✅ Uninstaller included
✅ Looks like commercial software
✅ Better for permanent installation
📦 Size: ~70-90 MB

### **Portable ZIP (Previous):**
✅ No installation needed
✅ Extract and run
✅ Can run from USB
✅ Good for testing
❌ No desktop shortcut
❌ No Start Menu entry
❌ Manual path navigation
📦 Size: ~60-80 MB

**Recommendation:** Use **Installer** for team distribution!

---

## 🎯 **Distribution Workflow**

### **Your Workflow:**

```
1. Make changes to code
   ↓
2. Run: DEPLOY_INSTALLER.bat
   ↓
3. Test the installer
   ↓
4. Share: SIP_Simulator_Setup_v1.0.exe
```

### **Teammate Workflow:**

```
1. Receive: SIP_Simulator_Setup_v1.0.exe
   ↓
2. Double-click installer
   ↓
3. Click "Next, Next, Install"
   ↓
4. Done! Desktop shortcut created
   ↓
5. Double-click desktop icon to launch
```

**No path navigation, no BAT files, just double-click!**

---

## 💡 **Key Improvements**

### **1. Desktop Shortcut** ✅
- **Before:** Users had to navigate to folder and run BAT file
- **After:** Just double-click desktop icon!

### **2. Professional Installer** ✅
- **Before:** Extract ZIP, find folder, run file
- **After:** Double-click installer, click Next, done!

### **3. Better Fonts** ✅
- **Before:** Emojis looked blocky
- **After:** Clean Segoe UI font, professional appearance

### **4. Start Menu Entry** ✅
- **Before:** No Start Menu entry
- **After:** Find in Start Menu like any Windows app

### **5. Uninstaller** ✅
- **Before:** Manual deletion
- **After:** Professional uninstaller in Control Panel

---

## 📁 **Files Created**

### **Installer Scripts:**
- `DEPLOY_INSTALLER.bat` - Master installer creation script
- `4_setup_inno_setup.bat` - Downloads Inno Setup
- `5_create_installer.bat` - Creates installer

### **Configuration:**
- `installer_script.iss` - Inno Setup configuration

### **Output:**
- `installer_output/SIP_Simulator_Setup_v1.0.exe` - Final installer

---

## ❓ **FAQ**

**Q: Do teammates need admin rights to install?**
A: No! Installer can install to user folder if no admin rights.

**Q: Can they choose installation location?**
A: Yes! Installer lets them choose where to install.

**Q: Will desktop shortcut work after moving files?**
A: Yes! Shortcut points to installation location.

**Q: How do they uninstall?**
A: Start Menu → SIP Simulator → Uninstall, or Control Panel.

**Q: Can I update the app?**
A: Yes! Create new installer, teammates run it to update.

**Q: What if they already have the portable version?**
A: They can install side-by-side or uninstall portable first.

**Q: Does it work on Windows 7?**
A: Yes! Works on Windows 7, 8, 10, and 11.

**Q: What about the fonts?**
A: Now uses Segoe UI - much cleaner and more professional!

---

## 🚀 **Ready to Create Installer!**

Just run:

```batch
DEPLOY_INSTALLER.bat
```

And you'll have a professional Windows installer with:
- ✅ Desktop shortcuts
- ✅ Start Menu entries
- ✅ Professional appearance
- ✅ Clean fonts
- ✅ Easy distribution

**Your team will love it!** 🎉
