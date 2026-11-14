# 🚀 SIP Simulator - Quick Start Deployment Guide

## 📌 **TL;DR - Deploy in 3 Steps**

```batch
1. Run: DEPLOY.bat
2. Wait: ~10-15 minutes (first time)
3. Share: SIP_Simulator_v2.0_Portable.zip
```

---

## 🎯 **What You Get**

### **Output:**
- `SIP_Simulator_v2.0_Portable.zip` (~60-80 MB)

### **What's Inside:**
```
SIP_Simulator_Package/
├── SIP_Simulator.exe          ← Double-click to run!
├── jre/                       ← Java (bundled, no install needed)
├── lib/                       ← Libraries
├── scenarios/                 ← SIP test scenarios
├── Icons/                     ← UI icons
├── security/                  ← TLS certificates
├── fonts/                     ← Emoji fonts
└── README.txt                 ← User instructions
```

---

## 📋 **Deployment Steps**

### **Option 1: ONE-CLICK (Recommended)**

```batch
DEPLOY.bat
```

This will:
1. ✅ Build the application
2. ✅ Download tools (Launch4j, JRE)
3. ✅ Create Windows EXE
4. ✅ Package everything
5. ✅ Create ZIP file

**Time:** 10-15 minutes (first time), 2-3 minutes (subsequent builds)

---

### **Option 2: STEP-BY-STEP (Manual Control)**

#### **Step 1: Build Application**
```batch
build.bat
```
**Output:** `SIP_Simulator.jar`

#### **Step 2: Setup Deployment Tools** (First time only)
```batch
1_setup_deployment_tools.bat
```
**Downloads:**
- Launch4j (~5 MB)
- Portable JRE 8 (~40 MB)

**Time:** ~5-10 minutes

#### **Step 3: Create EXE**
```batch
2_create_exe.bat
```
**Output:** `SIP_Simulator.exe`

#### **Step 4: Create Package**
```batch
3_create_package.bat
```
**Output:** 
- `SIP_Simulator_Package/` folder
- `SIP_Simulator_v2.0_Portable.zip`

---

## 📦 **Distribution**

### **For Your Team:**

1. **Share the ZIP:**
   - Upload to shared drive
   - Email (if < 25 MB limit, use file sharing service)
   - USB drive

2. **Instructions for Teammates:**
   ```
   1. Extract SIP_Simulator_v2.0_Portable.zip
   2. Open the extracted folder
   3. Double-click SIP_Simulator.exe
   4. Done! No installation needed.
   ```

3. **Important Notes:**
   - ✅ Works on any Windows 7+ machine
   - ✅ No Java installation required
   - ✅ No admin rights needed
   - ✅ Fully portable (can run from USB)
   - ✅ Can install anywhere (Desktop, Documents, etc.)

---

## 🧪 **Testing Before Distribution**

### **Quick Test (5 minutes):**

1. **Extract to different location:**
   ```
   C:\Temp\SIP_Test\
   ```

2. **Run the EXE:**
   ```
   Double-click SIP_Simulator.exe
   ```

3. **Verify:**
   - [ ] Application launches
   - [ ] Window title: "SIP Simulator"
   - [ ] Menus display with icons
   - [ ] Can load a scenario
   - [ ] Can change theme

4. **If all OK → Ready to distribute!**

---

### **Full Test (30 minutes):**

See `TESTING_CHECKLIST.md` for comprehensive testing.

---

## 🔧 **Troubleshooting**

### **Problem: DEPLOY.bat fails**

**Solution 1: Check Internet Connection**
- Tools need to be downloaded
- Requires stable internet

**Solution 2: Run Steps Manually**
```batch
build.bat
1_setup_deployment_tools.bat
2_create_exe.bat
3_create_package.bat
```

**Solution 3: Manual Download**
- Launch4j: https://launch4j.sourceforge.net/
- JRE 8: https://adoptium.net/temurin/releases/?version=8

---

### **Problem: EXE doesn't run on teammate's machine**

**Check:**
1. Windows version (needs Windows 7+)
2. Antivirus blocking (whitelist the EXE)
3. Extract fully (don't run from ZIP)
4. User permissions (try different folder)

---

### **Problem: "Java not found" error**

**Solution:**
- Ensure `jre/` folder is in same directory as EXE
- Check `launch4j_config.xml` has correct JRE path
- Rebuild package

---

### **Problem: Package too large (> 100 MB)**

**Normal size: 60-80 MB**

If larger:
- Check if multiple JRE copies exist
- Remove unnecessary files
- Compress better (use 7-Zip)

---

## 📁 **Path Management**

### **✅ GOOD - Relative Paths (Already Implemented)**

```java
// Scenarios
String scenariosPath = "scenarios" + File.separator + "scenario.txt";

// Icons
String iconPath = "Icons" + File.separator + "icon.png";

// Logs
String logPath = "logs" + File.separator + "log.txt";
```

### **❌ BAD - Hardcoded Paths (Avoid)**

```java
// DON'T DO THIS
String path = "C:\\Users\\<username>\\Desktop\\...";
```

**Your code already uses relative paths! ✅**

---

## 🎨 **Emoji Solution**

### **Current Status:**
- ✅ Colored circular icons on menu bar
- ✅ Unicode emojis in menu items (monochrome)

### **Why Not Colored Emojis?**
Java Swing doesn't support colored emoji rendering natively. The current solution (colored circles) is:
- ✅ Professional looking
- ✅ Works everywhere
- ✅ No dependencies
- ✅ Fast rendering

### **Alternative (If You Want Real Emojis):**
Use PNG images instead:
1. Create 16x16 PNG emoji images
2. Place in `Icons/` folder
3. Load as ImageIcon
4. More work, but looks better

**Recommendation:** Keep current colored circles for now, they look professional!

---

## 📊 **Version Management**

### **Current Version:** 1.0

### **For Future Updates:**

1. **Update version in:**
   - `launch4j_config.xml` (fileVersion, productVersion)
   - Window title (if desired)
   - README.txt

2. **Rebuild:**
   ```batch
   DEPLOY.bat
   ```

3. **Rename output:**
   ```
   SIP_Simulator_v2.1_Portable.zip
   ```

---

## 🎯 **Next Steps**

### **Today:**
1. ✅ Run `DEPLOY.bat`
2. ✅ Test the package
3. ✅ Share with one teammate (pilot test)
4. ✅ Get feedback

### **This Week:**
1. ✅ Fix any issues found
2. ✅ Full team distribution
3. ✅ Collect feedback
4. ✅ Plan next version

---

## 💡 **Pro Tips**

### **Faster Rebuilds:**
After first deployment, subsequent builds are faster:
- Tools already downloaded
- JRE already extracted
- Only need to rebuild JAR and repackage

### **Version Control:**
Keep these files in version control:
- All `.java` source files
- `build.bat`
- `launch4j_config.xml`
- Deployment scripts
- README and documentation

**Don't commit:**
- `build/` folder
- `deployment/` folder
- `SIP_Simulator_Package/` folder
- `.jar` and `.exe` files (too large)

### **Backup:**
Before major changes:
```batch
xcopy SIP_Simulator_Package SIP_Simulator_v2.0_BACKUP /E /I
```

---

## ❓ **FAQ**

**Q: Do teammates need Java installed?**
A: No! JRE is bundled in the package.

**Q: Can it run on Mac/Linux?**
A: No, this is Windows-only. For Mac/Linux, share the JAR file.

**Q: Can I update the app without redistributing?**
A: For minor updates, you can share just the new JAR file. For major updates, redistribute the full package.

**Q: How do I add new scenarios?**
A: Add `.txt` files to `scenarios/` folder, then rebuild.

**Q: Can users customize the app?**
A: Yes! They can:
- Add scenarios
- Change themes
- Configure settings
- Add TLS certificates

**Q: Is the app portable?**
A: Yes! Can run from USB drive, no installation needed.

---

## 📞 **Support**

**For Issues:**
1. Check `TESTING_CHECKLIST.md`
2. Check this guide's Troubleshooting section
3. Contact team lead

**For Feature Requests:**
1. Document the request
2. Discuss with team
3. Plan for next version

---

## ✅ **Deployment Checklist**

Before distributing to team:

- [ ] Application builds successfully
- [ ] EXE created successfully
- [ ] Package created successfully
- [ ] Tested on different path
- [ ] Tested on different machine (if possible)
- [ ] README.txt is accurate
- [ ] All features work
- [ ] No errors on startup
- [ ] File size reasonable (~60-80 MB)
- [ ] ZIP file extracts correctly

**If all checked → Ready to distribute! 🚀**

---

**Good luck with your deployment!** 🎉
