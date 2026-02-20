@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.ExperimentalForeignApi
import libgdal.*


class OgrVectorDataset(path: String) : GdalDataset(
    requireNotNull(
        GDALOpenEx(
            path,
            GDAL_OF_VECTOR.toUInt(),
            null,
            null,
            null
        )
    )
)
