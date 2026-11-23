# CarSplitScreen APK (Màn Android Ô tô)

Ứng dụng chia đôi màn hình tối giản nền tối cho màn Android ô tô (OLEDPRO, Android 7.0+):
- **Bên trái**: YouTube (mặc định) — bấm nút `YT` góc trên trái để chuyển nhanh sang **Bản đồ phụ (OpenStreetMap)** hoặc dán link web bất kỳ.
- **Bên phải**: Google Maps nền tối bảo vệ mắt ban đêm, hỗ trợ định vị GPS, tìm nhanh Trạm xăng / Trạm sạc / Bãi đỗ, xem Vệ tinh, và nút **Mở Maps gốc** dẫn đường giọng nói.
- Nút `Full` / `50/50` trên từng khung để phóng to 100% hoặc quay lại chia đôi.

## Cách xuất file `.apk` tự động qua GitHub Actions (không cần cài Android Studio)
1. Tạo 1 repository mới trên GitHub (ví dụ `CarSplitScreen`).
2. Giải nén gói ZIP này và push toàn bộ file + thư mục lên nhánh `main` (đảm bảo giữ nguyên thư mục `.github/workflows/build-apk.yml`).
3. Vào tab **Actions** trên GitHub -> đợi khoảng 1–2 phút để biên dịch xong.
4. Bấm vào lượt chạy màu xanh -> kéo xuống mục **Artifacts** -> tải `CarSplitScreen-APK`, giải nén lấy file `CarSplitScreen.apk` copy ra USB hoặc cài qua ADB lên màn ô tô.
