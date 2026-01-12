import os

target_dir = r"c:\Users\kazuh\Documents\AndroidStudioProjects\MusicPlayer\MusicPlayer_backup"

for root, dirs, files in os.walk(target_dir):
    for file in files:
        if file.endswith(".kt"):
            file_path = os.path.join(root, file)
            try:
                with open(file_path, "r", encoding="utf-8") as f:
                    content = f.read()
                
                new_content = content.replace("kazuh", "User")
                
                if new_content != content:
                    with open(file_path, "w", encoding="utf-8") as f:
                        f.write(new_content)
                    print(f"Updated: {file_path}")
            except Exception as e:
                print(f"Error processing {file_path}: {e}")
