# TAMAAZ Android Trial

هذا مشروع Android حقيقي لتطبيق TAMAAZ.

## ما يعمل داخل النسخة
- واجهة TAMAAZ المعتمدة.
- خرائط OpenStreetMap عبر MapLibre Web داخل WebView.
- GPS وصلاحية الموقع.
- البحث عبر Nominatim.
- حساب الطريق والمسارات البديلة عبر OSRM.
- أوضاع الأسرع / الأهدأ / الأوفر / أفضل شارع.
- البلاغات تحفظ محليًا على الهاتف في النسخة التجريبية.
- الوضع النهاري والليلي.

## بناء APK
يوجد Workflow جاهز في:
`.github/workflows/build-apk.yml`

عند رفع المشروع إلى GitHub، GitHub Actions يبني:
`app-debug.apk`

والملف يظهر داخل Artifacts باسم:
`TAMAAZ-debug-apk`

## ملاحظة
هذه نسخة Android تجريبية وليست إصدار Play Store نهائي. تحتاج الإنترنت للخرائط والبحث والتوجيه.
