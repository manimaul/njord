#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <gd.h>

// Function to crop a PNG image using libgd and return the new PNG as a byte array
unsigned char* crop_png_libgd(const char *filename, int x, int y, int width, int height, size_t *out_size) {
    FILE *fp = fopen(filename, "rb");
    if (!fp) {
        fprintf(stderr, "Error opening file %s for reading\n", filename);
        return NULL;
    }

    gdImagePtr im = gdImageCreateFromPngFile(fp);
    fclose(fp);

    if (!im) {
        fprintf(stderr, "Error creating image from PNG file %s\n", filename);
        return NULL;
    }

    int original_width = gdImageSX(im);
    int original_height = gdImageSY(im);

    if (x < 0 || y < 0 || width <= 0 || height <= 0 ||
        x >= original_width || y >= original_height ||
        x + width > original_width || y + height > original_height) {
        fprintf(stderr, "Error: Invalid crop dimensions.\n");
        gdImageDestroy(im);
        return NULL;
    }

    gdImagePtr cropped_im = gdImageCreateTrueColor(width, height);
    if (!cropped_im) {
        fprintf(stderr, "Error creating cropped image\n");
        gdImageDestroy(im);
        return NULL;
    }

    gdImageCopy(cropped_im, im, 0, 0, x, y, width, height);
    gdImageDestroy(im);

    unsigned char *png_data = NULL;
    int png_data_size = 0;

    png_data = (unsigned char*)gdImagePngPtr(cropped_im, &png_data_size);
    gdImageDestroy(cropped_im);

    if (png_data) {
        *out_size = (size_t)png_data_size;
        return png_data;
    } else {
        fprintf(stderr, "Error encoding cropped image to PNG\n");
        return NULL;
    }
}

// Example usage (requires libgd development files to be installed)
/*
int main() {
    const char *input_filename = "input.png";
    int crop_x = 50;
    int crop_y = 30;
    int crop_width = 100;
    int crop_height = 80;
    size_t cropped_size;

    unsigned char *cropped_data = crop_png_libgd(input_filename, crop_x, crop_y, crop_width, crop_height, &cropped_size);

    if (cropped_data) {
        printf("Cropped PNG data size: %zu bytes\n", cropped_size);

        // You can now write this cropped_data to a new PNG file or use it in memory.
        // For example, to write to "cropped_gd.png":
        FILE *fp_out = fopen("cropped_gd.png", "wb");
        if (fp_out) {
            fwrite(cropped_data, 1, cropped_size, fp_out);
            fclose(fp_out);
            printf("Cropped image written to cropped_gd.png\n");
        } else {
            fprintf(stderr, "Error opening cropped_gd.png for writing\n");
        }

        gdFree(cropped_data); // Use gdFree to free memory allocated by gdImagePngPtr
    } else {
        fprintf(stderr, "Error during PNG cropping with libgd.\n");
    }

    return 0;
}
*/