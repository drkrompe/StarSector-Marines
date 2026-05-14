package com.dillon.starsectormarines.assets;

import com.dillon.starsectormarines.assets.animation.Animation;
import com.dillon.starsectormarines.assets.material.MaterialInfo;
import com.dillon.starsectormarines.assets.animation.Skeleton;
import com.dillon.starsectormarines.assets.mesh.MeshData;
import java.util.List;
import java.util.Map;

public record LoadedModel(Map<Integer, MeshData> meshDataParts, List<MaterialInfo> materials, Skeleton skeleton, List<Animation> animations) {}
