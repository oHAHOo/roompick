# -*- coding: utf-8 -*-
"""
로컬 데모용 플레이스홀더 이미지를 DB에 직접 넣는다.

로컬 백엔드가 더미 AWS 키로 떠 있어 S3 업로드(IMAGE_004)가 실패하므로,
정식 업로드 경로 대신 image_url만 채운다. 로컬 개발 DB 전용이며
되돌리려면 아래 ROLLBACK SQL을 실행하면 된다.

  DELETE FROM accommodation_images WHERE image_url LIKE 'https://picsum.photos/%';
  DELETE FROM room_images          WHERE image_url LIKE 'https://picsum.photos/%';
"""
import subprocess
import sys

IMAGES_PER_ENTITY = 3
ACC_PREFIX = "rp-acc"
ROOM_PREFIX = "rp-room"


def mysql(sql, quiet=False):
    p = subprocess.run(
        ["docker", "exec", "-i", "roompick-mysql",
         "mysql", "-uroompick", "-proompick", "roompick", "--batch", "--skip-column-names", "-e", sql],
        capture_output=True, text=True, encoding="utf-8",
    )
    err = "\n".join(l for l in (p.stderr or "").splitlines() if "Using a password" not in l)
    if err.strip() and not quiet:
        print("SQL 오류:", err, file=sys.stderr)
    return (p.stdout or "").strip()


acc_ids = [int(x) for x in mysql("SELECT accommodation_id FROM accommodations ORDER BY 1;").split()]
room_ids = [int(x) for x in mysql("SELECT room_id FROM rooms ORDER BY 1;").split()]
print(f"대상: 숙소 {len(acc_ids)}개, 객실 {len(room_ids)}개 "
      f"(각 {IMAGES_PER_ENTITY}장)")

# 재실행해도 중복되지 않도록 기존 플레이스홀더를 먼저 지운다.
for _t in ("accommodation_images", "room_images"):
    mysql(f"DELETE FROM {_t} WHERE image_url LIKE 'https://loremflickr.com/%' "
          f"OR image_url LIKE 'https://picsum.photos/%';")


def rows(table, id_col, ids, prefix, kind):
    values = []
    for entity_id in ids:
        for order in range(IMAGES_PER_ENTITY):
            # seed를 고정해 같은 대상은 항상 같은 이미지가 나오게 한다.
            url = f"https://picsum.photos/seed/{prefix}-{entity_id}-{order}/1200/800"
            values.append(f"({entity_id}, '{url}', {order}, NOW(6), NOW(6))")
    sql = (f"INSERT INTO {table} ({id_col}, image_url, sort_order, created_at, updated_at) "
           f"VALUES {', '.join(values)};")
    mysql(sql)
    print(f"  {kind}: {len(values)}행 삽입")


rows("accommodation_images", "accommodation_id", acc_ids, ACC_PREFIX, "숙소 이미지")
rows("room_images", "room_id", room_ids, ROOM_PREFIX, "객실 이미지")

print("\n확인:")
print("  accommodation_images:", mysql("SELECT COUNT(*) FROM accommodation_images;"))
print("  room_images        :", mysql("SELECT COUNT(*) FROM room_images;"))
print("  샘플:", mysql("SELECT image_url FROM accommodation_images ORDER BY accommodation_image_id LIMIT 1;"))
