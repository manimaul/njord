# STEP 1
# import libraries
import fitz
import os.path
import io
from PIL import Image

# STEP 2
# file path you want to extract images from
file = "S-52_PresLib Ed 4.0.3 Part I Addendum_Clean.pdf"

# open the file
pdf_file = fitz.open(file)

script_dir = os.path.dirname(os.path.realpath(__file__))
img_out_dir = os.path.join(script_dir, "out/pdfimages")
os.makedirs(img_out_dir, exist_ok=True)

# STEP 3
# iterate over PDF pages
for page_index in range(len(pdf_file)):

    # get the page itself
    page = pdf_file[page_index]
    image_list = page.getImageList()

    # printing number of images found in this page
    if image_list:
        print(f"[+] Found a total of {len(image_list)} images in page {page_index}")
    else:
        print("[!] No images found on page", page_index)
    for image_index, img in enumerate(page.getImageList(), start=1):

        # get the XREF of the image
        xref = img[0]

        # extract the image bytes
        base_image = pdf_file.extractImage(xref)
        image_bytes = base_image["image"]

        # get the image extension
        image_ext = base_image["ext"]
        with open(os.path.join(img_out_dir, "{}.{}".format(image_index, image_ext)), 'wb') as fd:
            fd.write(image_bytes)
            # image = Image.new("RGBA", (int(base_image['width']), int(base_image['height'])))

