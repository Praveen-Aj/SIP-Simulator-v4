# SIP Simulator v4 - Licensing & Compliance Analysis
**Based on SIPInspector Open-Source Project**

---

## ⚠️ CRITICAL FINDING

**Your application is built on GPL v3-licensed code (SIPInspector).**

This has **SIGNIFICANT licensing implications** for distribution, deployment, and IP ownership.

---

## 🔍 LICENSING ANALYSIS

### SIPInspector Base Project
- **Source:** https://sourceforge.net/projects/sipinspector/
- **License:** GNU General Public License v3 (GPL v3)
- **Original Author:** Zarko Coklin
- **Evidence in Code:** Copyright headers in all source files:
  ```
  Copyright (C) 2008  Zarko Coklin
  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License
  ```

### Your Modifications
- Multi-codec support (PCMU/PCMA/G.722)
- SRTP encryption
- Scenario editor improvements
- Campaign testing framework
- Live audio enhancements
- Bug fixes and security improvements

**Status:** All your modifications are **derivative works** of GPL v3 code

---

## ❌ CRITICAL COMPLIANCE ISSUES

### Issue 1: GPL v3 Copyleft Obligation
**If you distribute/deploy the application, you MUST:**

✗ Provide complete source code to users/customers  
✗ License all modifications under GPL v3  
✗ Maintain attribution to original author  
✗ Document all changes made  
✗ Include full GPL v3 license text  

**Current Status:** ⚠️ **LIKELY NOT COMPLIANT** (need verification)

**Questions to Ask:**
- [ ] Have you provided source code to customers?
- [ ] Have you included GPL v3 license notice?
- [ ] Have you documented modifications?
- [ ] Have you attributed original author?

---

### Issue 2: TCS IP Ownership & Policy
**Potential conflicts:**

| TCS Policy Area | GPL v3 Requirement | Conflict? |
|---|---|---|
| Proprietary IP | Copyleft requires disclosure | ⚠️ YES |
| Closed-source products | GPL requires open-source | ⚠️ YES |
| Customer licensing | Must pass GPL terms to users | ⚠️ YES |
| IP warranties | GPL includes "AS-IS" warranty | ⚠️ POSSIBLE |
| Customer support/SLA | GPL has no support guarantees | ⚠️ POSSIBLE |

**Risk Level:** 🔴 **CRITICAL** - Likely violates TCS IP policy

---

### Issue 3: Distribution & Deployment Scenarios

#### Scenario A: Internal Tool Only
**Status:** ✅ SAFE
- GPL v3 allows internal use without modification
- No distribution = no copyleft obligations
- **BUT:** Once shipped to customer = distribution

#### Scenario B: Deployed for Single Customer
**Status:** ❌ NOT SAFE
- Deployment = distribution under GPL v3
- Customer has right to request source code
- Must provide source + license
- Customer can redistribute under GPL v3

#### Scenario C: Product License to Multiple Customers
**Status:** ❌ NOT SAFE
- Must provide source code to all customers
- All modifications must be open-source
- Cannot charge differently for closed-source version
- Violates commercial software model

#### Scenario D: Open-Source Release
**Status:** ✅ COMPLIANT
- Release all code under GPL v3
- Maintain attribution
- Document modifications
- Complies with GPL v3 terms

---

## 📋 WHAT TCS NEEDS TO KNOW

### TCS Compliance Checklist

- [ ] **Have you obtained legal review?** (CRITICAL)
- [ ] **Who is your customer?** (Internal/External/Public)
- [ ] **How will you distribute?** (Source/Binary/Deployment)
- [ ] **What's your commercial model?** (Free/Paid)
- [ ] **Have you attributed the original author?** (Required)
- [ ] **Have you documented all changes?** (Required)
- [ ] **Have you obtained GPL v3 compliance approval?** (Required)

### Required Actions

**BEFORE shipping to ANY customer:**

1. ✅ **Legal Review** (URGENT)
   - Have TCS Legal/Compliance review GPL v3 obligations
   - Confirm compliance with TCS IP policy
   - Get written approval for distribution model

2. ✅ **Attribution & Documentation**
   - Update copyright headers to include TCS
   - Document all modifications made
   - Create CHANGES.md listing all changes
   - Create CONTRIBUTORS.md mentioning Zarko Coklin

3. ✅ **License Compliance**
   - Include full GPL v3 license text (LICENSE.txt)
   - Include COPYING.txt with GPL v3 terms
   - If modifying, update copyright statements

4. ✅ **Source Code Availability**
   - Prepare to provide source code with each deployment
   - Ensure customer can rebuild from source
   - Document build process (BUILDING.md)

5. ✅ **Customer Communication**
   - Disclose GPL v3 licensing in contract
   - Explain customer's rights under GPL v3
   - Be clear about what source code will be provided

---

## 🚨 RISK ASSESSMENT

### If You DON'T Comply with GPL v3

**Potential Consequences:**
- ❌ Copyright infringement lawsuit by original author (Zarko Coklin)
- ❌ Forced open-source release of all code
- ❌ Damages + attorney fees (if commercial use)
- ❌ Cease & desist orders
- ❌ Reputational damage to TCS
- ❌ Customer liability issues

**Risk Level:** 🔴 **CRITICAL**

### If You DO Comply with GPL v3

**Benefits:**
- ✅ Legal compliance
- ✅ No copyright infringement risk
- ✅ Can contribute improvements to community
- ✅ Transparent about licensing
- ✅ Builds trust with customers

**Challenges:**
- ⚠️ Must open-source modifications
- ⚠️ Cannot claim proprietary ownership
- ⚠️ Cannot prevent customer modifications
- ⚠️ Affects commercial model

---

## 📋 RECOMMENDED OPTIONS

### Option 1: Internal Use Only (EASIEST) ✅
**Use Case:** Internal testing tool, not shipped to customers

**Compliance:** ✅ COMPLIANT
- GPL v3 allows unrestricted internal use
- No distribution = no obligations
- Can use as-is

**Action:** 
- [ ] Get written confirmation from Legal that it's internal-only
- [ ] Document this decision
- [ ] Only distribute internally

---

### Option 2: Open-Source Release (RECOMMENDED) ✅
**Use Case:** Release as open-source project under GPL v3

**Benefits:**
- ✅ Full GPL v3 compliance
- ✅ Contributes to community
- ✅ Can accept contributions
- ✅ No licensing issues
- ✅ TCS gets credit as maintainer

**Action:**
- [ ] Update copyright to include TCS
- [ ] Create documentation (README, CHANGES, etc.)
- [ ] Publish on GitHub/GitLab under GPL v3
- [ ] Consider dual-licensing (if compatible)
- [ ] Publicize as TCS contribution to open-source

**Timeline:** 1-2 weeks preparation

---

### Option 3: Obtain Alternative License (COMPLEX) ⚠️
**Use Case:** Want to use code commercially without open-sourcing

**Options:**
a) **Negotiate with Original Author**
   - Contact Zarko Coklin
   - Request dual-licensing (GPL v3 + Commercial)
   - Allows both open & closed-source use
   - **Cost:** Likely requires payment
   - **Timeline:** 2-4 weeks

b) **Rewrite from Scratch** (EXPENSIVE)
   - Don't use SIPInspector code
   - Write your own SIP simulator
   - Own 100% of IP
   - **Cost:** High (6-12 months effort)
   - **Timeline:** Long

c) **Use Different Base Project**
   - Find MIT/Apache 2.0 licensed SIP simulator
   - More permissive license
   - Lower compliance burden
   - **Cost/Timeline:** Depends on alternatives

---

## 🎯 WHAT TO DO RIGHT NOW

### This Week (URGENT):

1. **Contact TCS Legal/Compliance Team**
   ```
   Subject: Urgent - GPL v3 Licensing Review Required
   
   Body:
   - SIP Simulator project uses GPL v3-licensed code
   - Derived from SIPInspector (SourceForge)
   - Planning customer deployment
   - Need compliance review and approval
   - Attached: Licensing & compliance analysis
   ```

2. **Prepare Documentation for Legal**
   - List of all TCS modifications
   - Deployment/distribution plan
   - Customer licensing questions
   - This compliance analysis

3. **Do NOT ship to customers until approved**
   - Pause all customer deployments
   - Get written legal approval first
   - Document the approval

### Next Steps (After Legal Approval):

**If Approved for Internal-Only Use:**
- Continue current development
- No licensing changes needed
- Document "internal use only" policy

**If Approved for Customer Deployment:**
- Implement Option 2 (Open-Source) or Option 3 (Alt License)
- Update copyright/license headers
- Create required documentation
- Prepare source code for distribution

---

## 📄 REQUIRED DOCUMENTATION

### If Deploying to Customers

Create these files in the project root:

**1. LICENSE.txt**
```
Include full GNU General Public License v3 text
```

**2. COPYING.txt**
```
Copyright (C) 2008  Zarko Coklin (original author)
Copyright (C) 2026  Tata Consultancy Services (modifications)

This program is free software under GPL v3...
[Full GPL v3 terms]
```

**3. CHANGES.md**
```
# Modifications by TCS (2026-07-23 to 2026-08-01)

## New Features
- Multi-codec support (PCMU/PCMA/G.722)
- SRTP encryption (AES_CM_128_HMAC_SHA1_80)
- Campaign testing framework
- Per-test field values and PBX override

## Bug Fixes
- Live audio dropout (SSRC latching)
- Scenario editor edits being ignored
- Fixed media RTP port handling
- Fixed A-law encoder clipping

## Security Improvements
- Fixed SRTP scenario with valid 30-byte key
- Added codec negotiation

[Full changelog...]
```

**4. CONTRIBUTORS.md**
```
# Contributors

## Original Author
- Zarko Coklin (SIPInspector, SourceForge)

## Modifications by
- Tata Consultancy Services (2026)
- [Individual contributor names]
```

**5. README.md** (must mention license)
```
# SIP Simulator v4

This application is based on [SIPInspector](https://sourceforge.net/projects/sipinspector/)
and is licensed under the GNU General Public License v3.

See [LICENSE.txt](LICENSE.txt) for details.
```

---

## ✅ CHECKLIST FOR COMPLIANCE

### Legal & Licensing
- [ ] TCS Legal/Compliance review completed
- [ ] Written approval for deployment model
- [ ] Clear decision on Options 1/2/3
- [ ] Documentation of decision in writing

### Attribution & Documentation
- [ ] Original author credited in all files
- [ ] TCS copyright added to modified files
- [ ] CHANGES.md created and up-to-date
- [ ] CONTRIBUTORS.md includes original author
- [ ] License headers updated in code

### Code & Distribution
- [ ] LICENSE.txt file included
- [ ] COPYING.txt file included
- [ ] Build/installation instructions clear
- [ ] Source code can be provided to customers
- [ ] No proprietary code mixed in

### Customer Communication
- [ ] Licensing disclosed in contracts
- [ ] Customer understands GPL v3 rights
- [ ] Source code availability plan documented
- [ ] Support/warranty terms clarified
- [ ] Customer can redistribute if desired

---

## 🎓 KEY POINTS FOR YOUR SENIOR

**Tell Your Management:**

> "Our SIP Simulator is based on open-source GPL v3 code (SIPInspector). This means:
> 
> 1. **If internal-only:** No issues, we can use as-is
> 2. **If for customers:** We MUST open-source our code under GPL v3
> 3. **If commercial product:** We need legal approval + alternative licensing
> 
> We need TCS Legal review THIS WEEK before any customer deployments.
> 
> Recommended: Release as open-source project (fastest path, compliant, builds TCS reputation)."

---

## 📞 QUESTIONS FOR YOUR LEGAL TEAM

When contacting TCS Legal/Compliance:

1. **Is internal-only use compliant?** (Likely YES)
2. **Can we deploy to customers under GPL v3?** (Likely YES, but requires conditions)
3. **Does GPL v3 conflict with TCS IP policy?** (Need to clarify)
4. **What documentation is required?** (Attribution, source code, etc.)
5. **Can we pursue dual-licensing?** (Cost/benefit analysis)
6. **What's our liability if we don't comply?** (IP infringement risk)

---

## 🏁 BOTTOM LINE

**The Short Version:**

| Question | Answer | Action |
|----------|--------|--------|
| Is GPL v3 a problem? | ⚠️ YES - if distributing | Contact legal THIS WEEK |
| Can we use it internally? | ✅ YES - no issues | Continue development |
| Can we sell it? | ⚠️ ONLY with compliance | Need legal approval |
| Should we open-source? | ✅ RECOMMENDED - best path | Fast compliance + community |
| Risk if non-compliant? | 🔴 CRITICAL - lawsuit possible | High stakes |

**Action:** Contact TCS Legal/Compliance IMMEDIATELY before customer deployment.

---

**Report Classification:** Internal Use  
**Urgency:** CRITICAL  
**Next Review:** After legal team response

---

## 📎 APPENDIX: GPL v3 Key Terms

**In Plain English:**

- **Copyleft:** If you use GPL code, your changes must be GPL too
- **Source Code:** You must provide source to users
- **No Warranty:** Code provided "AS-IS" with no guarantees
- **Attribution:** Must credit original author
- **Freedom:** Users can copy, modify, distribute your code

---

## 🔗 USEFUL REFERENCES

- GPL v3 License: https://www.gnu.org/licenses/gpl-3.0.html
- Free Software Foundation FAQ: https://www.gnu.org/licenses/gpl-faq.html
- TCS Open Source Policy: [Contact your compliance team]
- SIPInspector Project: https://sourceforge.net/projects/sipinspector/

---

**Document Created:** August 1, 2026  
**Classification:** CONFIDENTIAL - Legal Review Required  
**Distribution:** TCS Legal, Management, Project Lead
